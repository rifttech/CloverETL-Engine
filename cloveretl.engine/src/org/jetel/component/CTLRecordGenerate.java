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
package org.jetel.component;

import java.util.Properties;

import org.jetel.ctl.CTLAbstractTransform;
import org.jetel.ctl.CTLEntryPoint;
import org.jetel.ctl.TransformLangExecutorRuntimeException;
import org.jetel.data.DataRecord;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.TransformException;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.util.ExceptionUtils;

/**
 * Base class of all Java transforms generated by CTL-to-Java compiler from CTL transforms in the DataGenerator
 * component.
 *
 * @author Michal Tomcanyi, Javlin a.s. &lt;michal.tomcanyi@javlin.cz&gt;
 * @author Martin Janik, Javlin a.s. &lt;martin.janik@javlin.eu&gt;
 *
 * @version 22nd June 2010
 * @created 27th July 2009
 *
 * @see RecordGenerate
 */
public abstract class CTLRecordGenerate extends CTLAbstractTransform implements RecordGenerate {

	/** Output data records used by the generator, or <code>null</code> if not accessible. */
	private DataRecord[] outputRecords = null;

	@Override
	public final boolean init(Properties parameters, DataRecordMetadata[] targetMetadata)
			throws ComponentNotReadyException {
		globalScopeInit();

		return initDelegate();
	}

	/**
	 * Called by {@link #init(Properties, DataRecordMetadata[])} to perform user-specific initialization defined
	 * in the CTL transform. The default implementation does nothing, may be overridden by the generated transform
	 * class.
	 *
	 * @throws ComponentNotReadyException if the initialization fails
	 */
	@CTLEntryPoint(name = RecordGenerateTL.INIT_FUNCTION_NAME, required = false)
	protected Boolean initDelegate() throws ComponentNotReadyException {
		// does nothing and succeeds by default, may be overridden by generated transform classes
		return true;
	}

	@Override
	public final int generate(DataRecord[] target) throws TransformException {
		int result = 0;

		// only output records are accessible within the generate() function
		outputRecords = target;

		try {
			result = generateDelegate();
		} catch (ComponentNotReadyException exception) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", exception);
		}

		// make the output records inaccessible again
		outputRecords = null;

		return result;
	}

	/**
	 * Called by {@link #generate(DataRecord[])} to generate user-specific data record defined in the CTL transform.
	 * Has to be overridden by the generated transform class.
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = RecordGenerateTL.GENERATE_FUNCTION_NAME, required = true)
	protected abstract Integer generateDelegate() throws ComponentNotReadyException, TransformException;

	@Override
	public int generateOnError(Exception exception, DataRecord[] target) throws TransformException {
		int result = 0;

		// only output records are accessible within the generate() function
		outputRecords = target;

		try {
			result = generateOnErrorDelegate(TransformUtils.getMessage(exception), ExceptionUtils.stackTraceToString(exception));
		} catch (UnsupportedOperationException ex) {
			// no custom error handling implemented, throw an exception so the transformation fails
			throw new TransformException("Generate failed!", exception);
		} catch (ComponentNotReadyException ex) {
			// the exception may be thrown by lookups, sequences, etc.
			throw new TransformException("Generated transform class threw an exception!", ex);
		}

		// make the output records inaccessible again
		outputRecords = null;

		return result;
	}

	/**
	 * Called by {@link #generateOnError(Exception, DataRecord[])} to generate user-specific data record defined
	 * in the CTL transform. May be overridden by the generated transform class.
	 * Throws <code>UnsupportedOperationException</code> by default.
	 *
	 * @param errorMessage an error message of the error message that occurred
	 * @param stackTrace a stack trace of the error message that occurred
	 *
	 * @throws ComponentNotReadyException if some internal initialization failed
	 * @throws TransformException if an error occurred
	 */
	@CTLEntryPoint(name = RecordGenerateTL.GENERATE_ON_ERROR_FUNCTION_NAME, parameterNames = {
			RecordTransformTL.ERROR_MESSAGE_PARAM_NAME, RecordTransformTL.STACK_TRACE_PARAM_NAME }, required = false)
	protected Integer generateOnErrorDelegate(String errorMessage, String stackTrace)
			throws ComponentNotReadyException, TransformException {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public final void signal(Object signalObject) {
		// does nothing
	}

	@Override
	public final Object getSemiResult() {
		return null;
	}

	@Override
	protected final DataRecord getInputRecord(int index) {
		throw new TransformLangExecutorRuntimeException(INPUT_RECORDS_NOT_ACCESSIBLE);
	}

	@Override
	protected final DataRecord getOutputRecord(int index) {
		if (outputRecords == null) {
			throw new TransformLangExecutorRuntimeException(OUTPUT_RECORDS_NOT_ACCESSIBLE);
		}

		if (index < 0 || index >= outputRecords.length) {
			throw new TransformLangExecutorRuntimeException(new Object[] { index }, OUTPUT_RECORD_NOT_DEFINED);
		}

		return outputRecords[index];
	}

}
