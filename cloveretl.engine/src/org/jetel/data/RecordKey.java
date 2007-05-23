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
package org.jetel.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetel.metadata.DataRecordMetadata;

/**
 *  This class serves the role of DataRecords comparator.<br>
 * It can compare two records with different structure based on
 * specified fields. It is used when sorting, hashing or (in general)
 * comparing data.<br>
 * <br>
 * <i>Usage:</i><br>
 * <code>
 * key = new RecordKey(keyFieldNames,recordMetadata);<br>
 * key.init();<br>
 * key.compare(recordA,recordB);
 * </code>
 *
 * @author      dpavlis
 * @since       May 2, 2002
 * @revision    $Revision$
 * @created     January 26, 2003
 */
public class RecordKey {

	private int keyFields[];
	private DataRecordMetadata metadata;
	private String keyFieldNames[];
	private final static char KEY_ITEMS_DELIMITER = ':';
	private final static int DEFAULT_STRING_KEY_LENGTH = 32;

	private StringBuffer keyStr;

	private boolean equalNULLs = false; // specifies whether two NULLs are deemed equal

	/**
	 *  Constructor for the RecordKey object
	 *
	 * @param  keyFieldNames  names of individual fields composing the key
	 * @param  metadata       metadata describing structure of DataRecord for which the key is built
	 * @since                 May 2, 2002
	 */
	public RecordKey(String keyFieldNames[], DataRecordMetadata metadata) {
		this.metadata = metadata;
		this.keyFieldNames = keyFieldNames;
	}

	/**
	 * @param keyFields indices of fields composing the key
	 * @param metadata metadata describing structure of DataRecord for which the key is built
	 */
	public RecordKey(int keyFields[], DataRecordMetadata metadata) {
		this.metadata = metadata;
		this.keyFields = keyFields;
	}
	
	// end init

	/**
	 *  Assembles string representation of the key based on current record's value.
	 *
	 * @param  record  DataRecord whose field's values will be used to create key string.
	 * @return         The KeyString value
	 * @since          May 2, 2002
	 */
	public String getKeyString(DataRecord record) {
		
		if (keyStr == null){ 
			keyStr = new StringBuffer(DEFAULT_STRING_KEY_LENGTH);
		}else{ 
			keyStr.setLength(0); 
		}
		for (int i = 0; i < keyFields.length; i++) {
			keyStr.append(record.getField(keyFields[i]).toString());
			// not used for now keyStr.append(KEY_ITEMS_DELIMITER);
		}
		return keyStr.toString();
	}


	/**
	 *  Performs initialization of internal data structures
	 *
	 * @since    May 2, 2002
	 */
	public void init() {

	    if (keyFields == null) {
            Integer position;
            keyFields = new int[keyFieldNames.length];
            Map fields = metadata.getFieldNames();

            for (int i = 0; i < keyFieldNames.length; i++) {
                if ((position = (Integer) fields.get(keyFieldNames[i])) != null) {
                    keyFields[i] = position.intValue();
                } else {
                    throw new RuntimeException(
                            "Field name specified as a key doesn't exist: "
                                    + keyFieldNames[i]);
                }
            }
        }else if (keyFieldNames==null){
            keyFieldNames=new String[keyFields.length];
            for (int i=0;i<keyFields.length;i++){
                keyFieldNames[i]=metadata.getField(keyFields[i]).getName();
            }
        }
	}


	/**
	 *  Gets the keyFields attribute of the RecordKey object
	 *
	 * @return    The keyFields value
	 */
	public int[] getKeyFields() {
		return keyFields;
	}

    
    /**
     * Gets fields (indexes) which are not part of the key
     * 
     * @return
     * @since 31.1.2007
     */
    public int[] getNonKeyFields(){
        Set<Integer> allFields=new LinkedHashSet<Integer>();
        for(int i=0;i<metadata.getNumFields();i++){
            allFields.add(new Integer(i));
        }
        allFields.removeAll(Arrays.asList(keyFields));
        int[] nonKey=new int[allFields.size()];
        int counter=0;
        for(Integer index : allFields){
            nonKey[counter++]=index.intValue();
        }
        return nonKey;
    }

	/**
	 * Gets number of fields defined by this key.
	 * @return length of key
	 */
	public int getLenght() {
	    return keyFields.length;
	}
	
	/**
	 *  Compares two records (of the same layout) based on defined key-fields and returns (-1;0;1) if (< ; = ; >)
	 *
	 * @param  record1  Description of the Parameter
	 * @param  record2  Description of the Parameter
	 * @return          -1 ; 0 ; 1
	 */
	public int compare(DataRecord record1, DataRecord record2) {
		int compResult;
		if (record1.getMetadata() != record2.getMetadata()) {
			throw new RuntimeException("Can't compare - records have different metadata associated." +
					" Possibly different structure");
		}
		if (equalNULLs){
		    for (int i = 0; i < keyFields.length; i++) {
		        compResult = record1.getField(keyFields[i]).compareTo(record2.getField(keyFields[i]));
		        if (compResult != 0) {
		            if (!(record1.getField(keyFields[i]).isNull&&record2.getField(keyFields[i]).isNull)){
		                return compResult;
		            }
		        }
		    }
		}else {
		    for (int i = 0; i < keyFields.length; i++) {
		        compResult = record1.getField(keyFields[i]).compareTo(record2.getField(keyFields[i]));
		        if (compResult != 0) {
		            return compResult;
		        }
		    }
		}
		return 0;
		// seem to be the same
	}


	/**
	 *  Compares two records (can have different layout) based on defined key-fields
	 *  and returns (-1;0;1) if (< ; = ; >).<br>
	 *  The particular fields to be compared have to be of the same type !
	 *
	 * @param  secondKey  RecordKey defined for the second record
	 * @param  record1    First record
	 * @param  record2    Second record
	 * @return            -1 ; 0 ; 1
	 */
	public int compare(RecordKey secondKey, DataRecord record1, DataRecord record2) {
		int compResult;
		int[] record2KeyFields = secondKey.getKeyFields();
		if (keyFields.length != record2KeyFields.length) {
			throw new RuntimeException("Can't compare. keys have different number of DataFields");
		}

		if (equalNULLs){
		    for (int i = 0; i < keyFields.length; i++) {
		        compResult = record1.getField(keyFields[i]).compareTo(record2.getField(record2KeyFields[i]));
		        if (compResult != 0) {
		            if (!(record1.getField(keyFields[i]).isNull&&record2.getField(keyFields[i]).isNull)){
		                return compResult;
		            }
		        }
		    }
		}else{
		    
		    for (int i = 0; i < keyFields.length; i++) {
		        compResult = record1.getField(keyFields[i]).compareTo(record2.getField(record2KeyFields[i]));
		        if (compResult != 0) {
		            return compResult;
		        }
		    }
		}
		return 0;
		// seem to be the same
	}


	/**
	 *  Description of the Method
	 *
	 * @param  record1  Description of the Parameter
	 * @param  record2  Description of the Parameter
	 * @return          Description of the Return Value
	 */
	public boolean equals(DataRecord record1, DataRecord record2) {
		if (record1.getMetadata() != record2.getMetadata()) {
			throw new RuntimeException("Can't compare - records have different metadata associated." +
					" Possibly different structure");
		}
		if (equalNULLs){
		    for (int i = 0; i < keyFields.length; i++) {
		        if (!record1.getField(keyFields[i]).equals(record2.getField(keyFields[i]))) {
		            if (!(record1.getField(keyFields[i]).isNull&&record2.getField(keyFields[i]).isNull)){
		                return false;
		            }
		        }
		    }
		}else{
		    for (int i = 0; i < keyFields.length; i++) {
		        if (!record1.getField(keyFields[i]).equals(record2.getField(keyFields[i]))) {
		            return false;
		        }
		    }
		}
		return true;
	}
	

	/**
	 * This method serializes (saves) content of key fields only (for specified record) into
	 * buffer.
	 * 
	 * @param buffer ByteBuffer into which serialize key fields
	 * @param record data record from which key fields will be serialized into ByteBuffer
	 */
	public void serializeKeyFields(ByteBuffer buffer,DataRecord record) {
		for (int i = 0; i < keyFields.length; i++) {
			record.getField(keyFields[i]).serialize(buffer);
		}
	}
	
    /**
     *  This method deserializes (restores) content of key fields only (for specified record) from
     * buffer.
     * 
     * @param buffer ByteBuffer from which deserialize key fields
     * @param record data record whose key fields will be deserialized from ByteBuffer
     * @since 29.1.2007
     */
    public void deserializeKeyFileds(ByteBuffer buffer,DataRecord record){
        for (int i = 0; i < keyFields.length; i++) {
            record.getField(keyFields[i]).deserialize(buffer);
        }
    }
    
    
	/**
	 * This method creates DataRecordMetadata object which represents fields composing this key. It can
	 * be used for creating data record composed from key fields only.
	 * @return DataRecordMetadata object
	 */
	public DataRecordMetadata generateKeyRecordMetadata(){
		DataRecordMetadata metadata = new DataRecordMetadata(this.metadata.getName()+"key");
		for (int i = 0; i < keyFields.length; i++) {
			metadata.addField(this.metadata.getField(keyFields[i]));
		}
		return metadata;
	}
	
	/**
     * toString method: creates a String representation of the object
     * @return the String representation
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("RecordKey[");
        if (keyFields == null) {
            buffer.append("keyFields = ").append("null");
        } else {
            buffer.append("keyFields = ").append("[");
            for (int i = 0; i < keyFields.length; i++) {
                if (i != 0) {
                    buffer.append(", ");
                }
                buffer.append(keyFields[i]);
            }
            buffer.append("]");
        }
        buffer.append(", metadata = ").append(metadata.toString());
        if (keyFieldNames == null) {
            buffer.append(", keyFieldNames = ").append("null");
        } else {
            buffer.append(", keyFieldNames = ").append(Arrays.asList(keyFieldNames).toString());
        }
        buffer.append(", KEY_ITEMS_DELIMITER = ").append(KEY_ITEMS_DELIMITER);
        buffer.append(", DEFAULT_KEY_LENGTH = ").append(DEFAULT_STRING_KEY_LENGTH);
        buffer.append(", EQUAL_NULLS = ").append(equalNULLs);
        buffer.append(", keyStr = ").append(keyStr);
        buffer.append("]");
        return buffer.toString();
    }

    /**
     * True if two NULL values (fields with NULL flag set) are considered equal
     * 
     * @return Returns the equalNULLs.
     */
    public boolean isEqualNULLs() {
        return equalNULLs;
    }
    /**
     * Sets whether two NULL values (fields with NULL flag set) are considered equal.<br>
     * Default is false.
     * 
     * @param equalNULLs The equalNULLs to set.
     */
    public void setEqualNULLs(boolean equalNULLs) {
        this.equalNULLs = equalNULLs;
    }
    
    /**
     * This method checks if two RecordKeys are comparable
     * 
     * @param secondKey
     * @return Integer array with numbers of incomparable fields, odd numbers are from this key 
     * 	and even numbers are from second key. When lenghts of the keys differ, proper numbers are
     * 	returned as null. When keys are comparable there is returned array of length zero.
     */
    public Integer[] getIncomparableFields(RecordKey secondKey){
    	List<Integer> incomparable = new ArrayList<Integer>();
		int[] record2KeyFields = secondKey.getKeyFields();
		DataRecordMetadata secondMetadata = secondKey.metadata;
		for (int i = 0; i < Math.max(keyFields.length, record2KeyFields.length); i++) {
			if (i<keyFields.length && i<record2KeyFields.length) {
				if (metadata.getFieldType(keyFields[i]) != secondMetadata.getFieldType(record2KeyFields[i])) {
					incomparable.add(keyFields[i]);
					incomparable.add(record2KeyFields[i]);
				}
			}else if (i>=keyFields.length) {
				incomparable.add(null);
				incomparable.add(record2KeyFields[i]);
			}else {
				incomparable.add(keyFields[i]);
				incomparable.add(null);
			}
		}
		
    	return incomparable.toArray(new Integer[0]);
    }
    
}
// end RecordKey


