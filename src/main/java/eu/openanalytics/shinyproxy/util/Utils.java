/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.shinyproxy.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

public class Utils {

	public static String JDBC_DRIVER_NAME = "oracle.jdbc.driver.OracleDriver";
	public static String JDBC_CONNECTION = "jdbc:oracle:thin:@localhost:1521:xe";
	public static String DB_USERNAME = "shiny";
	public static String DB_PASSWORD = "shavar";

	public static boolean retry(IntPredicate job, int tries, int waitTime) {
		return retry(job, tries, waitTime, false);
	}

	public static boolean retry(IntPredicate job, int tries, int waitTime, boolean retryOnException) {
		boolean retVal = false;
		RuntimeException exception = null;
		for (int currentTry = 1; currentTry <= tries; currentTry++) {
			try {
				if (job.test(currentTry)) {
					retVal = true;
					exception = null;
					break;
				}
			} catch (RuntimeException e) {
				if (retryOnException)
					exception = e;
				else
					throw e;
			}
			try {
				Thread.sleep(waitTime);
			} catch (InterruptedException ignore) {
			}
		}
		if (exception == null)
			return retVal;
		else
			throw exception;

	}

	public static Long memoryToBytes(String memory) {
		if (memory == null || memory.isEmpty())
			return null;
		Matcher matcher = Pattern.compile("(\\d+)([bkmg]?)").matcher(memory.toLowerCase());
		if (!matcher.matches())
			throw new IllegalArgumentException("Invalid memory argument: " + memory);
		long mem = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2);
		switch (unit) {
		case "k":
			mem *= 1024;
			break;
		case "m":
			mem *= 1024 * 1024;
			break;
		case "g":
			mem *= 1024 * 1024 * 1024;
			break;
		default:
		}
		return mem;
	}

	public static Logger loggerForThisClass() {
		// We use the third stack element; second is this method, first is
		// .getStackTrace()
		StackTraceElement myCaller = Thread.currentThread().getStackTrace()[2];
		// Assert.equal("<clinit>", myCaller.getMethodName());
		return Logger.getLogger(myCaller.getClassName());
	}

	public static Connection connect() {
		Connection connection = null;
		try {
			if (connection == null || connection.isClosed()) {

				Class.forName(JDBC_DRIVER_NAME);
				connection = DriverManager.getConnection(JDBC_CONNECTION, DB_USERNAME, DB_PASSWORD);
			}
		} catch (Exception e) {			
			e.printStackTrace();
		}

		return connection;
	}
}
