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
package org.jetel.ctl.extensions;

import java.util.Locale;
import java.util.Objects;

import org.jetel.ctl.TransformLangExecutorRuntimeException;
import org.jetel.util.MiscUtils;
import org.jetel.util.formatter.DateFormatter;
import org.jetel.util.formatter.DateFormatterFactory;
import org.jetel.util.formatter.TimeZoneProvider;
import org.jetel.util.string.StringUtils;

/**
 * @author jakub (info@cloveretl.com)
 *         (c) (c) Javlin, a.s. (www.javlin.eu) (www.cloveretl.com)
 *
 * @created May 25, 2010
 */
public class TLDateFormatLocaleCache extends TLCache {

	private DateFormatter cachedFormatter;
	private String previousLocaleString;
	private String previousTimeZoneString;
	
	private Locale previousLocale;
	private TimeZoneProvider previousTimeZone;
	private final TLFunctionCallContext context;
	
	private TLDateFormatLocaleCache(TLFunctionCallContext context) {
		this.context = context;
		this.previousLocale = context.getDefaultLocale();
		this.previousTimeZone = context.getDefaultTimeZone();
	}
	
	/**
	 * @deprecated Use {@link #TLDateFormatLocaleCache(TLFunctionCallContext, int, int, int)} instead. 
	 */
	@Deprecated
	public TLDateFormatLocaleCache(TLFunctionCallContext context, int patternPos, int localePos) {
		this(context);
		createCachedLocaleFormat(context, patternPos, localePos, -1);
	}

	public TLDateFormatLocaleCache(TLFunctionCallContext context, int patternPos, int localePos, int timeZonePos) {
		this(context);
		createCachedLocaleFormat(context, patternPos, localePos, timeZonePos);
	}
	
	public void createCachedLocaleFormat(TLFunctionCallContext context, int patternPos, int localePos, int timeZonePos) {
		
		if (context.getLiteralsSize() <= patternPos) {
			return;
		}
		
		Object paramPattern = context.getParamValue(patternPos);

		// CLO-1190 - null check added
		if ((paramPattern != null) && !(paramPattern instanceof String)) {
			return;
		}
		
		// this construction which allows overloading, where you can use the parameter on the same position as 'format' but 
		// in different meaning with overloaded functions (as long as it is not 'String')
		if (context.getLiteralsSize() <= localePos || !(context.getParamValue(localePos) instanceof String)) {
			String pattern = (String) paramPattern;
			if (context.isLiteral(patternPos)) {
				cachedFormatter = DateFormatterFactory.getFormatter(pattern, context.getDefaultLocale(), context.getDefaultTimeZone());
			}
			return;
		}
		
		if (context.getLiteralsSize() > timeZonePos) {
			if (context.isLiteral(patternPos) && context.isLiteral(localePos) && context.isLiteral(timeZonePos)) {
				String pattern = (String) paramPattern;
				String paramLocale = (String) context.getParamValue(localePos);
				String paramTimeZone = (String) context.getParamValue(timeZonePos);
				cachedFormatter = DateFormatterFactory.getFormatter(pattern, getLocale(paramLocale), getTimeZone(paramTimeZone));
			}
		} else {
			if (context.isLiteral(patternPos) && context.isLiteral(localePos)) {
				String pattern = (String) paramPattern;
				String paramLocale = (String) context.getParamValue(localePos);
				cachedFormatter = DateFormatterFactory.getFormatter(pattern, getLocale(paramLocale), context.getDefaultTimeZone());
			}
		}
		
	}
	
	private Locale getLocale(String localeString) {
		Locale locale = previousLocale;
		if (!Objects.equals(localeString, previousLocaleString)) {
			if (StringUtils.isEmpty(localeString)) {
				locale = context.getDefaultLocale();
			} else {
				locale = MiscUtils.createLocale(localeString);
			}
		}
		previousLocaleString = localeString;
		previousLocale = locale;
		return locale;
	}
	
	private TimeZoneProvider getTimeZone(String timeZoneString) {
		TimeZoneProvider timeZone = previousTimeZone;
		if (!Objects.equals(timeZoneString, previousTimeZoneString)) {
			if (StringUtils.isEmpty(timeZoneString)) {
				timeZone = context.getDefaultTimeZone();
			} else {
				timeZone = new TimeZoneProvider(timeZoneString);
			}
		}
		previousTimeZoneString = timeZoneString;
		previousTimeZone = timeZone;
		return timeZone;
	}

	
	private DateFormatter getCachedFormatter3(TLFunctionCallContext context, 
			String format, String localeString, String timeZoneString,
			int patternPos, int localePos, int timeZonePos) {
		// if we use the variant with format, locale and time zone specified
		if ((context.isLiteral(patternPos) && context.isLiteral(localePos) && context.isLiteral(timeZonePos))
				// either format, locale and timeZone were literals (thus cached at init)
					
				|| (cachedFormatter != null 
						&& format.equals(cachedFormatter.getPattern()) 
						// careful when locale or timeZone is null! See test_convertlib_date2str.ctl
						&& Objects.equals(localeString, previousLocaleString)
						&& Objects.equals(timeZoneString, previousTimeZoneString)
					)
				// or format is already cached and previous inputs match the current ones
				)
		{
			return cachedFormatter;
		} else {
			// otherwise we have to recompute cache and remember just in the case future input will be the same
			cachedFormatter = DateFormatterFactory.getFormatter(format, getLocale(localeString), getTimeZone(timeZoneString));

			return cachedFormatter;
		}
	}
	
	private DateFormatter getCachedFormatter2(TLFunctionCallContext context, 
			String format, String locale,
			int patternPos, int localePos) {
		// we use the variant with format and locale specified
		if ((context.isLiteral(patternPos) && context.isLiteral(localePos))
				// either format, locale and timeZone were literals (thus cached at init)
					
				|| (cachedFormatter != null 
						&& format.equals(cachedFormatter.getPattern()) 
						// careful when locale or timeZone is null! See test_convertlib_date2str.ctl
						&& Objects.equals(locale, previousLocaleString)
					)
				// or format is already cached and previous inputs match the current ones
				)
			{
				return cachedFormatter;
			} else {
				// otherwise we have to recompute cache and remember just in the case future input will be the same
				cachedFormatter = DateFormatterFactory.getFormatter(format, getLocale(locale), context.getDefaultTimeZone());

				return cachedFormatter;
			}
	}

	private DateFormatter getCachedFormatter1(TLFunctionCallContext context, 
			String format, 
			int patternPos) {
		// just format is specified, but not locale
		if (context.isLiteral(patternPos) 
				|| (cachedFormatter != null && format.equals(cachedFormatter.getPattern()))
			) 
		{
			return cachedFormatter;
		} else {
			// same as above but just for format (default locale and time zone is used) 
			cachedFormatter = DateFormatterFactory.getFormatter(format, context.getDefaultLocale(), context.getDefaultTimeZone());

			return cachedFormatter;				
		}
	}

	/**
	 * @deprecated Use {@link #getCachedLocaleFormatter(TLFunctionCallContext, String, String, String, int, int, int)} instead.
	 */
	@Deprecated
	public DateFormatter getCachedLocaleFormatter(TLFunctionCallContext context, 
			String format, String locale,
			int patternPos, int localePos) {
		return getCachedLocaleFormatter(context, format, locale, null, patternPos, localePos, -1);
	}

	public DateFormatter getCachedLocaleFormatter(TLFunctionCallContext context, 
			String format, String locale, String timeZone,
			int patternPos, int localePos, int timeZonePos) {
		
		// context.getLiteralsSize() actually returns the number of parameters, not just the number of literals
		
		if (context.getLiteralsSize() > Math.max(Math.max(patternPos, localePos), timeZonePos)) {
			// if we use the variant with format, locale and time zone specified
			return getCachedFormatter3(context, format, locale, timeZone, patternPos, localePos, timeZonePos);
		} else if (context.getLiteralsSize() > Math.max(patternPos, localePos) && context.getLiteralsSize() <= timeZonePos) {
			// if we use the variant with format and locale specified
			return getCachedFormatter2(context, format, locale, patternPos, localePos);
		} else if (context.getLiteralsSize() > patternPos && context.getLiteralsSize() <= localePos) {
			// if we use the variant with only format specified
			return getCachedFormatter1(context, format, patternPos);
		}
		
		throw new TransformLangExecutorRuntimeException("Format not correctly specified for the date.");
	}
}
