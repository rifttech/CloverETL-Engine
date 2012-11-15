/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.hadoop.component;

import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetel.data.DataRecord;
import org.jetel.data.DataRecordFactory;
import org.jetel.data.Defaults;
import org.jetel.data.lookup.LookupTable;
import org.jetel.database.IConnection;
import org.jetel.enums.PartitionFileTagType;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.graph.InputPort;
import org.jetel.graph.Node;
import org.jetel.graph.Result;
import org.jetel.graph.TransformationGraph;
import org.jetel.hadoop.connection.HadoopConnection;
import org.jetel.util.MultiFileWriter;
import org.jetel.util.SynchronizeUtils;
import org.jetel.util.property.ComponentXMLAttributes;
import org.w3c.dom.Element;

/**
 * @author David Pavlis (info@cloveretl.com) (c) Javlin a.s. (www.javlin.eu)
 * 
 * @since
 * @revision $Revision: $
 */

public class HadoopWriter extends Node {

	private static final String XML_APPEND_ATTRIBUTE = "append";
	private static final String XML_FILEURL_ATTRIBUTE = "fileURL";
	private static final String XML_RECORD_SKIP_ATTRIBUTE = "recordSkip";
	private static final String XML_RECORD_COUNT_ATTRIBUTE = "recordCount";
	private static final String XML_RECORDS_PER_FILE = "recordsPerFile";
	private static final String XML_BYTES_PER_FILE = "bytesPerFile";
	private static final String XML_MK_DIRS_ATTRIBUTE = "makeDirs";
	private static final String XML_PARTITIONKEY_ATTRIBUTE = "partitionKey";
	private static final String XML_PARTITION_ATTRIBUTE = "partition";
	private static final String XML_PARTITION_OUTFIELDS_ATTRIBUTE = "partitionOutFields";
	private static final String XML_PARTITION_FILETAG_ATTRIBUTE = "partitionFileTag";
	private static final String XML_KEY_FIELD_NAME_ATTRIBUTE = "keyField";
	private static final String XML_VALUE_FIELD_NAME_ATTRIBUTE = "valueField";
	private static final String XML_CONNECTION_ATTRIBUTE = "connection";

	private boolean mkDir;
	private String fileURL;
	private String connectionID;
	private boolean appendData;
	private String keyField;
	private String valueField;
	private HadoopConnection connection;
	private IHadoopSequenceFileFormatter formatter;
	private MultiFileWriter writer;
	private int skip;
	private int numRecords;
	private int recordsPerFile;
	private int bytesPerFile;

	private String partition;
	private String attrPartitionKey;
	private LookupTable lookupTable;
	private String attrPartitionOutFields;
	private PartitionFileTagType partitionFileTagType = PartitionFileTagType.NUMBER_FILE_TAG;

	public final static String COMPONENT_TYPE = "HADOOP_WRITER";
	private final static int READ_FROM_PORT = 0;
	// private final static int OUTPUT_PORT = 0;

	private static Log logger = LogFactory.getLog(HadoopWriter.class);

	/**
	 * Constructor
	 * 
	 * @param id
	 * @param fileURL
	 * @param charset
	 * @param appendData
	 * @param fields
	 */
	public HadoopWriter(String id, String fileURL, String connectionID, String keyField, String valueField,
			boolean appendData) {
		super(id);
		this.fileURL = fileURL;
		this.connectionID = connectionID;
		this.appendData = appendData;
		this.keyField = keyField;
		this.valueField = valueField;
	}

	/*
	 * (non-Javadoc)
	 * @see org.jetel.graph.Node#getType()
	 */
	@Override
	public String getType() {
		return COMPONENT_TYPE;
	}

	@Override
	public void preExecute() throws ComponentNotReadyException {
		super.preExecute();

		if (firstRun()) {
			writer.init(getInputPort(READ_FROM_PORT).getMetadata());
		} else {
			writer.reset();
		}
	}

	@Override
	public Result execute() throws Exception {
		InputPort inPort = getInputPort(READ_FROM_PORT);
		DataRecord record = DataRecordFactory.newRecord(inPort.getMetadata());
		record.init();
		while (record != null && runIt) {
			record = inPort.readRecord(record);
			if (record != null) {
				writer.write(record);
			}
			SynchronizeUtils.cloverYield();
		}
		writer.finish();
		return runIt ? Result.FINISHED_OK : Result.ABORTED;
	}

	@Override
	public void postExecute() throws ComponentNotReadyException {
		super.postExecute();
		try {
			writer.close();
		} catch (IOException e) {
			throw new ComponentNotReadyException(COMPONENT_TYPE + ": " + e.getMessage(), e);
		}
	}

	@Override
	public synchronized void free() {
		super.free();
		try {
			if (writer != null) {
				writer.close();
				writer = null;
			}
			if (connection != null) {
				connection.free();
				connection = null;
			}
		} catch (Throwable t) {
			logger.warn("Resource releasing failed for '" + getId() + "'. " + t.getMessage(), t);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.jetel.graph.GraphElement#checkConfig()
	 */
	@Override
	public ConfigurationStatus checkConfig(ConfigurationStatus status) {
		super.checkConfig(status);

		if (!checkInputPorts(status, 1, 1) || !checkOutputPorts(status, 0, 1)) {
			return status;
		}

		/* can't easily check writability - would need also Hadoop connection */

		return status;
	}

	private void prepareConnection() throws ComponentNotReadyException {
		IConnection conn = getGraph().getConnection(connectionID);
		if (conn == null) {
			throw new ComponentNotReadyException(this, "Can't find HadoopConnection ID: " + connectionID);
		}
		if (!(conn instanceof HadoopConnection)) {
			throw new ComponentNotReadyException(this, "Connection with ID: " + connectionID
					+ " isn't instance of the HadoopConnection class - " + conn.getClass().toString());
		}

		logger.debug(String.format("Connecting to HDFS via [%s].", conn.getId()));

		conn.init();
		this.connection = (HadoopConnection) conn;
	}

	/*
	 * (non-Javadoc)
	 * @see org.jetel.graph.GraphElement#init()
	 */
	@Override
	public void init() throws ComponentNotReadyException {
		if (isInitialized())
			return;
		super.init();

		initLookupTable();

		if (connection == null)
			prepareConnection();

		try {
			formatter = connection.getConnection().createFormatter(this.keyField, this.valueField, !this.appendData);
		} catch (IOException e) {
			throw new ComponentNotReadyException(this, e);
		}

		formatter.setGraph(getGraph());

		// based on file mask, create/open output file
		writer = new MultiFileWriter(formatter, null /* no context */, fileURL);

		writer.setLogger(logger);
		writer.setRecordsPerFile(recordsPerFile);
		writer.setAppendData(appendData);
		writer.setSkip(skip);
		writer.setNumRecords(numRecords);
		writer.setDictionary(getGraph().getDictionary());
		if (attrPartitionKey != null) {
			writer.setLookupTable(lookupTable);
			writer.setPartitionKeyNames(attrPartitionKey.split(Defaults.Component.KEY_FIELDS_DELIMITER_REGEX));
			writer.setPartitionFileTag(partitionFileTagType);
			// writer.setPartitionUnassignedFileName(partitionUnassignedFileName);
			if (attrPartitionOutFields != null) {
				writer.setPartitionOutFields(attrPartitionOutFields
						.split(Defaults.Component.KEY_FIELDS_DELIMITER_REGEX));
			}
		}

		writer.setOutputPort(getOutputPort(OUTPUT_PORT)); // for port protocol:
		// target file writes
		// data
		writer.setMkDir(mkDir);
	}

	/**
	 * Creates and initializes lookup table.
	 * 
	 * @throws ComponentNotReadyException
	 */
	private void initLookupTable() throws ComponentNotReadyException {
		if (partition == null)
			return;

		// Initializing lookup table
		lookupTable = getGraph().getLookupTable(partition);
		if (lookupTable == null) {
			throw new ComponentNotReadyException("Lookup table \"" + partition + "\" not found.");
		}
		if (!lookupTable.isInitialized()) {
			lookupTable.init();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.jetel.graph.Node#fromXML(org.jetel.graph.TransformationGraph, org.w3c.dom.Element)
	 */
	public static Node fromXML(TransformationGraph graph, Element nodeXML) {
		ComponentXMLAttributes xattribs = new ComponentXMLAttributes(nodeXML, graph);
		HadoopWriter aDataWriter = null;

		try {
			aDataWriter = new HadoopWriter(xattribs.getString(Node.XML_ID_ATTRIBUTE),
					xattribs.getString(XML_FILEURL_ATTRIBUTE), xattribs.getString(XML_CONNECTION_ATTRIBUTE),
					xattribs.getString(XML_KEY_FIELD_NAME_ATTRIBUTE),
					xattribs.getString(XML_VALUE_FIELD_NAME_ATTRIBUTE),
					xattribs.getBoolean(XML_APPEND_ATTRIBUTE, false));
			if (xattribs.exists(XML_RECORD_SKIP_ATTRIBUTE)) {
				aDataWriter.setSkip(Integer.parseInt(xattribs.getString(XML_RECORD_SKIP_ATTRIBUTE)));
			}
			if (xattribs.exists(XML_RECORD_COUNT_ATTRIBUTE)) {
				aDataWriter.setNumRecords(Integer.parseInt(xattribs.getString(XML_RECORD_COUNT_ATTRIBUTE)));
			}
			if (xattribs.exists(XML_RECORDS_PER_FILE)) {
				aDataWriter.setRecordsPerFile(xattribs.getInteger(XML_RECORDS_PER_FILE));
			}
			if (xattribs.exists(XML_BYTES_PER_FILE)) {
				aDataWriter.setBytesPerFile(xattribs.getInteger(XML_BYTES_PER_FILE));
			}
			if (xattribs.exists(XML_PARTITIONKEY_ATTRIBUTE)) {
				aDataWriter.setPartitionKey(xattribs.getString(XML_PARTITIONKEY_ATTRIBUTE));
			}
			if (xattribs.exists(XML_PARTITION_ATTRIBUTE)) {
				aDataWriter.setPartition(xattribs.getString(XML_PARTITION_ATTRIBUTE));
			}
			if (xattribs.exists(XML_PARTITION_FILETAG_ATTRIBUTE)) {
				aDataWriter.setPartitionFileTag(xattribs.getString(XML_PARTITION_FILETAG_ATTRIBUTE));
			}
			if (xattribs.exists(XML_PARTITION_OUTFIELDS_ATTRIBUTE)) {
				aDataWriter.setPartitionOutFields(xattribs.getString(XML_PARTITION_OUTFIELDS_ATTRIBUTE));
			}
			if (xattribs.exists(XML_MK_DIRS_ATTRIBUTE)) {
				aDataWriter.setMkDirs(xattribs.getBoolean(XML_MK_DIRS_ATTRIBUTE));
			}
		} catch (Exception ex) {
			System.err.println(COMPONENT_TYPE + ":" + xattribs.getString(Node.XML_ID_ATTRIBUTE, "unknown ID") + ":"
					+ ex.getMessage());
			return null;
		}

		return aDataWriter;
	}

	/*
	 * (non-Javadoc)
	 * @see org.jetel.graph.Node#toXML(org.w3c.dom.Element)
	 */
	@Override
	public void toXML(org.w3c.dom.Element xmlElement) {
		super.toXML(xmlElement);
		xmlElement.setAttribute(XML_FILEURL_ATTRIBUTE, this.fileURL);
		xmlElement.setAttribute(XML_CONNECTION_ATTRIBUTE, this.connectionID);
		xmlElement.setAttribute(XML_KEY_FIELD_NAME_ATTRIBUTE, this.keyField);
		xmlElement.setAttribute(XML_VALUE_FIELD_NAME_ATTRIBUTE, this.valueField);

		xmlElement.setAttribute(XML_APPEND_ATTRIBUTE, String.valueOf(this.appendData));
		if (skip != 0) {
			xmlElement.setAttribute(XML_RECORD_SKIP_ATTRIBUTE, String.valueOf(skip));
		}
		if (numRecords != 0) {
			xmlElement.setAttribute(XML_RECORD_COUNT_ATTRIBUTE, String.valueOf(numRecords));
		}
		if (recordsPerFile > 0) {
			xmlElement.setAttribute(XML_RECORDS_PER_FILE, Integer.toString(recordsPerFile));
		}
		if (bytesPerFile > 0) {
			xmlElement.setAttribute(XML_BYTES_PER_FILE, Integer.toString(bytesPerFile));
		}
		if (partition != null) {
			xmlElement.setAttribute(XML_PARTITION_ATTRIBUTE, partition);
		} else if (lookupTable != null) {
			xmlElement.setAttribute(XML_PARTITION_ATTRIBUTE, lookupTable.getId());
		}
		if (attrPartitionKey != null) {
			xmlElement.setAttribute(XML_PARTITIONKEY_ATTRIBUTE, attrPartitionKey);
		}
		if (attrPartitionOutFields != null) {
			xmlElement.setAttribute(XML_PARTITION_OUTFIELDS_ATTRIBUTE, attrPartitionOutFields);
		}
		xmlElement.setAttribute(XML_PARTITION_FILETAG_ATTRIBUTE, partitionFileTagType.name());
	}

	/**
	 * Sets number of skipped records in next call of getNext() method.
	 * 
	 * @param skip
	 */
	public void setSkip(int skip) {
		this.skip = skip;
	}

	/**
	 * Sets number of written records.
	 * 
	 * @param numRecords
	 */
	public void setNumRecords(int numRecords) {
		this.numRecords = numRecords;
	}

	public void setBytesPerFile(int bytesPerFile) {
		this.bytesPerFile = bytesPerFile;
	}

	public void setRecordsPerFile(int recordsPerFile) {
		this.recordsPerFile = recordsPerFile;
	}

	/**
	 * Sets lookup table for data partition.
	 * 
	 * @param lookupTable
	 */
	public void setLookupTable(LookupTable lookupTable) {
		this.lookupTable = lookupTable;
	}

	/**
	 * Gets lookup table for data partition.
	 * 
	 * @return
	 */
	public LookupTable getLookupTable() {
		return lookupTable;
	}

	/**
	 * Gets partition (lookup table id) for data partition.
	 * 
	 * @param partition
	 */
	public void setPartition(String partition) {
		this.partition = partition;
	}

	/**
	 * Gets partition (lookup table id) for data partition.
	 * 
	 * @return
	 */
	public String getPartition() {
		return partition;
	}

	/**
	 * Sets partition key for data partition.
	 * 
	 * @param partitionKey
	 */
	public void setPartitionKey(String partitionKey) {
		this.attrPartitionKey = partitionKey;
	}

	/**
	 * Gets partition key for data partition.
	 * 
	 * @return
	 */
	public String getPartitionKey() {
		return attrPartitionKey;
	}

	/**
	 * Sets fields which are used for file output name.
	 * 
	 * @param partitionOutFields
	 */
	public void setPartitionOutFields(String partitionOutFields) {
		attrPartitionOutFields = partitionOutFields;
	}

	/**
	 * Sets number file tag for data partition.
	 * 
	 * @param partitionKey
	 */
	public void setPartitionFileTag(String partitionFileTagType) {
		this.partitionFileTagType = PartitionFileTagType.valueOfIgnoreCase(partitionFileTagType);
	}

	/**
	 * Gets number file tag for data partition.
	 * 
	 * @return
	 */
	public PartitionFileTagType getPartitionFileTag() {
		return partitionFileTagType;
	}

	/**
	 * Sets make directory.
	 * 
	 * @param mkDir
	 *            - true - creates output directories for output file
	 */
	public void setMkDirs(boolean mkDir) {
		this.mkDir = mkDir;
	}

}
