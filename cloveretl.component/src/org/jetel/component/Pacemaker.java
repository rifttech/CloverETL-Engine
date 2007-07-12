/*
 *    jETeL/Clover - Java based ETL application framework.
 *    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
 *    
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *    
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
 *    Lesser General Public License for more details.
 *    
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.jetel.component;

import java.nio.ByteBuffer;

import org.jetel.data.Defaults;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.ConfigurationProblem;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.exception.XMLConfigurationException;
import org.jetel.graph.InputPortDirect;
import org.jetel.graph.Node;
import org.jetel.graph.Result;
import org.jetel.graph.TransformationGraph;
import org.jetel.util.ComponentXMLAttributes;
import org.jetel.util.StringUtils;
import org.jetel.util.SynchronizeUtils;
import org.w3c.dom.Element;

/**
 * <h3>Pacemaker Component</h3>
 * 
 * <!-- All records from input port:0 are copied onto all connected output ports
 * (multiplies number of records by number of defined output ports) -->
 * 
 * <table border="1">
 * <tr>
 * <th colspan="2">Component</th>
 * </tr>
 * <tr>
 * <td>
 * <h4><i>Name:</i></h4>
 * </td>
 * <td>Pacemaker</td>
 * </tr>
 * <tr>
 * <td>
 * <h4><i>Category:</i></h4>
 * </td>
 * <td>Transformer</td>
 * </tr>
 * <tr>
 * <td>
 * <h4><i>Description:</i></h4>
 * </td>
 * <td>All records from input port 0 are copied to all output ports with speed
 * limited by arguments "period" or "delay".</td>
 * </tr>
 * <tr>
 * <td>
 * <h4><i>Inputs:</i></h4>
 * </td>
 * <td>[0]- input records</td>
 * </tr>
 * <tr>
 * <td>
 * <h4><i>Outputs:</i></h4>
 * </td>
 * <td>At least one connected output port.</td>
 * </tr>
 * <tr>
 * <td>
 * <h4><i>Comment:</i></h4>
 * </td>
 * <td> </td>
 * </tr>
 * </table> <br>
 * <table border="1">
 * <tr>
 * <th colspan="2">XML attributes</th>
 * </tr>
 * <tr>
 * <td><b>type</b></td>
 * <td>"PACEMAKER"</td>
 * </tr>
 * <tr>
 * <td><b>id</b></td>
 * <td>component identification</td>
 * </tr>
 * <tr>
 * <td><b>period</b></td>
 * <td>Time in milliseconds for one row processing (from start to start), ie.
 * 250 means 4 rows per second. Default value = 0 (ignored). <br>This time is
 * minimum processing time - real row processing might take longer.</td>
 * </tr>
 * <tr>
 * <td><b>delay</b></td>
 * <td>Time in milliseconds to be delayed between two rows are processed (from
 * end to start). Default value = 0 (ignored). If specified, argument "period"
 * is ignored.</td>
 * </tr>
 * </table>
 * 
 * @author <a href="mailto:milan.zila@javlinconsulting.cz">mzila</a>
 * @since 12.7.2007
 */
public class Pacemaker extends Node {

	public final static String COMPONENT_TYPE = "PACEMAKER";

	private static final String XML_PERIOD_ATTRIBUTE = "period";

	private static final String XML_DELAY_ATTRIBUTE = "delay";

	private final static int READ_FROM_PORT = 0;

	private ByteBuffer recordBuffer;

	private int period = 0;

	private int delay = 0;

	/**
	 * @param id
	 */
	public Pacemaker(String id) {
		super(id);
	}

	@Override
	public Result execute() throws Exception {
		InputPortDirect inPort = (InputPortDirect) getInputPort(READ_FROM_PORT);
		boolean isData = true;
		long nextReadingTime = System.currentTimeMillis();
		while (isData && runIt) {
			if (delay > 0) {
				Thread.sleep(delay);
			} else if (period > 0) {
				while (nextReadingTime > System.currentTimeMillis()) {
					Thread.sleep(50);
				}
				nextReadingTime += period;
			}
			isData = inPort.readRecordDirect(recordBuffer);
			if (isData) {
				writeRecordBroadcastDirect(recordBuffer);
			}
			SynchronizeUtils.cloverYield();
		}
		broadcastEOF();
		return runIt ? Result.FINISHED_OK : Result.ABORTED;
	}

	/**
	 * @exception ComponentNotReadyException
	 */
	public void init() throws ComponentNotReadyException {
		super.init();
		recordBuffer = ByteBuffer.allocateDirect(Defaults.Record.MAX_RECORD_SIZE);
		if (recordBuffer == null) {
			throw new ComponentNotReadyException(
					"Can NOT allocate internal record buffer ! Required size:"
							+ Defaults.Record.MAX_RECORD_SIZE);
		}
	}

	public void toXML(Element xmlElement) {
		super.toXML(xmlElement);
		if (period != 0) {
			xmlElement.setAttribute(XML_PERIOD_ATTRIBUTE,
					String.valueOf(period));
		}
		if (delay != 0) {
			xmlElement.setAttribute(XML_DELAY_ATTRIBUTE, String.valueOf(delay));
		}
	}

	public static Node fromXML(TransformationGraph graph, Element xmlElement)
			throws XMLConfigurationException {
		ComponentXMLAttributes xattribs = new ComponentXMLAttributes(
				xmlElement, graph);
		Pacemaker pacemaker;
		try {
			pacemaker = new Pacemaker(xattribs.getString(XML_ID_ATTRIBUTE));
			if (xattribs.exists(XML_PERIOD_ATTRIBUTE)) {
				pacemaker.period = xattribs.getInteger(XML_PERIOD_ATTRIBUTE);
			}
			if (xattribs.exists(XML_DELAY_ATTRIBUTE)) {
				pacemaker.delay = xattribs.getInteger(XML_DELAY_ATTRIBUTE);
			}
		} catch (Exception ex) {
			throw new XMLConfigurationException(COMPONENT_TYPE + ":"
					+ xattribs.getString(XML_ID_ATTRIBUTE, " unknown ID ")
					+ ":" + ex.getMessage(), ex);
		}
		return pacemaker;
	}

	@Override
	public ConfigurationStatus checkConfig(ConfigurationStatus status) {
		super.checkConfig(status);

		checkInputPorts(status, 1, 1);
		checkOutputPorts(status, 1, Integer.MAX_VALUE);
		checkMetadata(status, getInMetadata(), getOutMetadata());

		try {
			init();
			free();
		} catch (ComponentNotReadyException e) {
			ConfigurationProblem problem = new ConfigurationProblem(
					e.getMessage(), ConfigurationStatus.Severity.ERROR, this,
					ConfigurationStatus.Priority.NORMAL);
			if (!StringUtils.isEmpty(e.getAttributeName())) {
				problem.setAttributeName(e.getAttributeName());
			}
			status.add(problem);
		}

		return status;
	}

	public String getType() {
		return COMPONENT_TYPE;
	}

}
