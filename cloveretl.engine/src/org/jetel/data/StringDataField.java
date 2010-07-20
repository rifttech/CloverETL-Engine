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
package org.jetel.data;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.text.RuleBasedCollator;

import org.jetel.data.primitive.StringFormat;
import org.jetel.exception.BadDataFormatException;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.util.bytes.ByteBufferUtils;
import org.jetel.util.string.Compare;

/**
 *  A class that represents String type data field.<br>
 *  It can hold String of arbitrary length, however due
 *  to use of short value as length specifier when serializing/
 * deserializing value to/from ByteBuffer, the maximum length
 * is limited to 32762 characters (Short.MAX_VALUE);
 *
 * @author      D.Pavlis
 * @since       March 27, 2002
 * @revision    $Revision$
 * @created     January 26, 2003
 * @see         org.jetel.metadata.DataFieldMetadata
 */
public class StringDataField extends DataField implements CharSequence{
	
	private static final long serialVersionUID = 6350085938993427855L;
	
	private StringBuilder value;
	private StringFormat stringFormat = null;
	
	/**
	 *  An attribute that represents ...
	 *
	 * @since
	 */
	private final static int INITIAL_STRING_BUFFER_CAPACITY = 32;

	private static final int SIZE_OF_CHAR = 2;

	/**
	 *  Constructor for the StringDataField object
	 *
	 * @param  _metadata  Metadata describing field
	 * @since             April 23, 2002
	 */
	public StringDataField(DataFieldMetadata _metadata) {
		this(_metadata,false);
	}

    /**
     * Constructor for the StringDataField object
     * 
     * @param _metadata Metadata describing field
     * @param plain <i>not used (only for compatibility reason)</i>
     */
    public StringDataField(DataFieldMetadata _metadata,boolean plain) {
        super(_metadata);
        if (_metadata.getSize() < 1) {
            value = new StringBuilder(INITIAL_STRING_BUFFER_CAPACITY);
        } else {
            value = new StringBuilder(_metadata.getSize());
        }
        // handle format string
        String regExp;
        regExp = _metadata.getFormatStr();
        if ((regExp != null) && (regExp.length() != 0)) {
        	stringFormat = StringFormat.create(regExp);
        } 
    }

	public StringDataField(DataFieldMetadata _metadata, String _value) {
		this(_metadata,false);
		setValue(_value);
	}

	private StringDataField(DataFieldMetadata _metadata, CharSequence _value){
	    super(_metadata);
	    this.value=new StringBuilder(_value.length());
	    this.value.append(_value);
	}

	public DataField duplicate(){
	    StringDataField newField=new StringDataField(metadata,value);
	    newField.setNull(this.isNull());
	    return newField;
	}
	
	/**
	 * @see org.jetel.data.DataField#copyField(org.jetel.data.DataField)
     * @deprecated use setValue(DataField) instead
	 */
	public void copyFrom(DataField fieldFrom){
	    if (fieldFrom instanceof StringDataField ){
	        if (!fieldFrom.isNull){
	            this.value.setLength(0);
	            this.value.append(((StringDataField)fieldFrom).value);
	        }
	        setNull(fieldFrom.isNull);
	    } else {
	        super.copyFrom(fieldFrom);
	    }
	}
	
	/**
	 *  Sets the Value attribute of the StringDataField object
	 *
	 * @param  value                       The new value to set. Valid types are char[] or CharSequence descendant, or <code>null</code>
	 * @exception  BadDataFormatException  When <code>null</code> value was set, but metadata definition requires field not-nullability
     * @exception  IllegalArgumentException When value of types other then char[], CharSequence descendant is passed to the function <code>value</code>
	 * @since                              April 23, 2002
	 */
	public void setValue(Object value) throws BadDataFormatException {
        if(value == null || value instanceof CharSequence) {
            setValue((CharSequence) value);
        } else if (value instanceof char[]) {
            setValue(new String((char[]) value));
        } else {
            throw new IllegalArgumentException(getMetadata().getName() + " field can not be set from class " + value.getClass().getName());
        }
	}

	@Override
	public void setValue(DataField fieldFrom) {
        if (fieldFrom instanceof StringDataField) {
            if (!fieldFrom.isNull){
                this.value.setLength(0);
                this.value.append(((StringDataField)fieldFrom).value);
            }
            setNull(fieldFrom.isNull);
        } else {
            if (fieldFrom != null) {
                setValue(fieldFrom.toString());
            } else {
                setNull(true);
            }
        }
	}
	
	/**
	 *  Sets the value attribute of the StringDataField object
	 *
	 * @param  seq  The character sequence from which to set the value (either
	 *      String, StringBuffer or Char Buffer)
	 * @since       October 29, 2002
	 */
	void setValue(CharSequence seq) {
		value.setLength(0);
		if (seq != null) {
		    value.append(seq);
			setNull(false);
		} else {
		    setNull(true);
		}
	}

    public void append(CharSequence seq) {
        if(isNull) {
            setValue(seq);
        } else {
            value.append(seq);
        }
    }

    /**
	 *  Sets the Null value indicator
	 *
	 * @param  isNull  The new Null value
	 * @since          October 29, 2002
	 */
	public void setNull(boolean isNull) {
		super.setNull(isNull);
		if (this.isNull) {
			value.setLength(0);
		}
	}

	/**
	 *  Gets the Null value indicator
	 *
	 * @return    The Null value
	 * @since     October 29, 2002
	 */
	public boolean isNull() {
		return super.isNull();
	}

    
    public void reset(){
        if (metadata.isNullable()){
            setNull(true);
        }else if (metadata.isDefaultValueSet()){
            setToDefaultValue();
        }else{
            value.setLength(0);
        }
    }

	/**
	 *  Gets the Value attribute of the StringDataField object
	 *
	 * @return    The Value value
	 * @since     April 23, 2002
	 */
	public Object getValue() {
	    return (isNull ? null : value);
	}

    public Object getValueDuplicate() {
        return (isNull ? null : new StringBuilder(value));
    }

	/**
	 *  Gets the value of the StringDataField object as charSequence
	 *
	 * @return    The charSequence value
	 */
	public CharSequence getCharSequence() {
		return (isNull ? null : value);
	}


	/**
	 *  Gets the Type attribute of the StringDataField object
	 *
	 * @return    The Type value
	 * @since     April 23, 2002
	 */
	public char getType() {
		return DataFieldMetadata.STRING_FIELD;
	}

	public void fromByteBuffer(ByteBuffer dataBuffer, CharsetDecoder decoder) throws CharacterCodingException {
		fromString(decoder.decode(dataBuffer));
	}

	public void toByteBuffer(ByteBuffer dataBuffer, CharsetEncoder encoder) throws CharacterCodingException {
		try {
			dataBuffer.put(encoder.encode(CharBuffer.wrap(value)));
		} catch (BufferOverflowException e) {
			throw new RuntimeException("The size of data buffer is only " + dataBuffer.limit() + ". Set appropriate parameter in defautProperties file.", e);
		}
	}

    @Override
    public void toByteBuffer(ByteBuffer dataBuffer) {
        if(!isNull) {
    		try {
    			dataBuffer.put(Charset.defaultCharset().encode(value.toString()));
    		} catch (BufferOverflowException e) {
    			throw new RuntimeException("The size of data buffer is only " + dataBuffer.limit() + ". Set appropriate parameter in defautProperties file.", e);
    		}
        }
    }

	public String toString() {
		if (isNull) {
			return metadata.getNullValue();
		}

		return value.toString();
	}

	public void fromString(CharSequence seq) {
		if (seq == null || Compare.equals(seq, metadata.getNullValue())) {
			setValue((CharSequence) null);
			return;
		}

		if (stringFormat != null && !stringFormat.matches(seq)) {
			throw new BadDataFormatException(String.format("%s (%s) cannot be set to \"%s\" - doesn't match defined format \"%s\"",
					getMetadata().getName(),DataFieldMetadata.type2Str(getType()),seq,stringFormat.getPattern()), seq.toString());
		}

		setValue(seq);
	}

	public void serialize(ByteBuffer buffer) {
	    final int length = value.length();
	    
		try {
			// encode nulls as zero, increment length of non-null values by one
			ByteBufferUtils.encodeLength(buffer, isNull ? 0 : length + 1);
	
			for(int counter = 0; counter < length; counter++) {
				buffer.putChar(value.charAt(counter));
			}
    	} catch (BufferOverflowException e) {
    		throw new RuntimeException("The size of data buffer is only " + buffer.limit() + ". Set appropriate parameter in defautProperties file.", e);
    	}
	}

	public void deserialize(ByteBuffer buffer) {
		// encoded length is incremented by one, decrement it back to normal
		final int length = ByteBufferUtils.decodeLength(buffer) - 1;

		// empty value - so we can store new string
		value.setLength(0);

		if (length < 0) {
			setNull(true);
		} else {
			for (int counter = 0; counter < length; counter++) {
				value.append(buffer.getChar());
			}
			setNull(false);
		}
	}

	public boolean equals(Object obj) {
	    if (isNull || obj==null ) return false;
	    if (this==obj) return true;
		CharSequence data;
		
		if (obj instanceof StringDataField){
		    if (((StringDataField)obj).isNull()) return false;
			data = (CharSequence)((StringDataField) obj).getValue();
		}else if (obj instanceof CharSequence){
			data = (CharSequence)obj;
		}else{
	        return false;
		}
		
		if (value.length() != data.length()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) != data.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 *  Compares this object with the specified object for order.
	 *
	 * @param  obj  Any object implementing CharSequence interface
	 * @return      -1;0;1 based on comparison result
	 */
	public int compareTo(Object obj) {
	    CharSequence strObj;
	    
		if (isNull) return -1;

        if (obj instanceof StringDataField) {
            if (((StringDataField) obj).isNull())
                return 1;
            strObj=((StringDataField) obj).value;
        }else if (obj instanceof CharSequence) {
            strObj = (CharSequence) obj;
        }else {
            throw new ClassCastException("Can't compare StringDataField to "
                    + obj.getClass().getName());
        }

      return Compare.compare(value, strObj);
        
	}

    /**
     * Compares this object with the specified object for order -
     * respecting i18n particularities - e.g. "e" versus "??".<br>
     * Using this method requires lots of resources and is therefore
     * much slower than simple compareTo(Object obj) method.
     *
     * @param  obj  Any object implementing CharSequence interface
     * @param collator Collator which should be used to compare
     * string representations respecting i18n particularities
     * @return      -1;0;1 based on comparison result
     */
    public int compareTo(Object obj,RuleBasedCollator collator) {
        CharSequence strObj;
        
        if (isNull) return -1;

        if (obj instanceof StringDataField) {
            if (((StringDataField) obj).isNull())
                return 1;
            strObj=((StringDataField) obj).value;
        }else if (obj instanceof CharSequence) {
            strObj = (CharSequence) obj;
        }else {
            throw new ClassCastException("Can't compare StringDataField to "
                    + obj.getClass().getName());
        }

        return Compare.compare(value, strObj, collator);
    }

    
	public int hashCode(){
		int hash=5381;
		for (int i=0;i<value.length();i++){
			hash = ((hash << 5) + hash) + value.charAt(i); 
		}
		return (hash & 0x7FFFFFFF);
	}
	
	/**
	 *  Returns how many bytes will be occupied when this field with current
	 *  value is serialized into ByteBuffer
	 *
	 * @return    The size value
	 * @see	      org.jetel.data.DataField
	 */
	@Override
	public int getSizeSerialized() {
		// length in characters multiplied of 2 (each char occupies 2 bytes in UNICODE) plus
		// size of length indicator (basically int variable)
	    final int length = value.length();
	    
		return length * SIZE_OF_CHAR + ByteBufferUtils.lengthEncoded(length + 1); //this incrementation is necessary due 'null/empty string' encoding, see serialize/deserialize methods
	}
	
	public char charAt(int position){
		return value.charAt(position);
	}
	
	public int length(){
		return value.length();
	}
	
	public CharSequence subSequence(int start, int end){
		return value.subSequence(start,end);
	}
}
