/*
 * Copyright (c) 2004, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

package com.impossibl.postgres.jdbc.shared;

import com.impossibl.postgres.jdbc.TestUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class GetObject310Test {

  private static final TimeZone saveTZ = TimeZone.getDefault();

  private static final ZoneOffset UTC = ZoneOffset.UTC; // +0000 always
  private static final ZoneOffset GMT03 = ZoneOffset.of("+03:00"); // +0300 always
  private static final ZoneOffset GMT05 = ZoneOffset.of("-05:00"); // -0500 always
  private static final ZoneOffset GMT13 = ZoneOffset.of("+13:00"); // +1300 always

  private Connection con;

  @Before
  public void setUp() throws Exception {
    con = TestUtil.openDB();
    TestUtil.createTable(con, "table1", "timestamp_without_time_zone_column timestamp without time zone,"
            + "timestamp_with_time_zone_column timestamp with time zone,"
            + "date_column date,"
            + "time_without_time_zone_column time without time zone,"
            + "time_with_time_zone_column time with time zone"
    );
  }

  @After
  public void tearDown() throws SQLException {
    TimeZone.setDefault(saveTZ);
    TestUtil.dropTable(con, "table1");
    TestUtil.closeDB(con);
  }

  /**
   * Test the behavior getObject for date columns.
   */
  @Test
  public void testGetLocalDate() throws SQLException {
    try (Statement stmt = con.createStatement()) {
      stmt.executeUpdate(TestUtil.insertSQL("table1", "date_column", "DATE '1999-01-08'"));

      try (ResultSet rs = stmt.executeQuery(TestUtil.selectSQL("table1", "date_column"))) {
        assertTrue(rs.next());
        LocalDate localDate = LocalDate.of(1999, 1, 8);
        assertEquals(localDate, rs.getObject("date_column", LocalDate.class));
        assertEquals(localDate, rs.getObject(1, LocalDate.class));
      }
    }
  }

  /**
   * Test the behavior getObject for time columns.
   */
  @Test
  public void testGetLocalTime() throws SQLException {
    try (Statement stmt = con.createStatement()) {
      stmt.executeUpdate(TestUtil.insertSQL("table1", "time_without_time_zone_column", "TIME '04:05:06.123456'"));
      stmt.executeUpdate(TestUtil.insertSQL("table1", "time_without_time_zone_column", "TIME '00:00:00'"));
      stmt.executeUpdate(TestUtil.insertSQL("table1", "time_without_time_zone_column", "TIME '23:59:59.999999'"));
      stmt.executeUpdate(TestUtil.insertSQL("table1", "time_without_time_zone_column", "TIME '23:59:59.9999999'"));

      try (ResultSet rs = stmt.executeQuery(TestUtil.selectSQL("table1", "time_without_time_zone_column"))) {
        assertTrue(rs.next());
        assertEquals(LocalTime.of(4, 5, 6, 123456000), rs.getObject(1, LocalTime.class));
        assertTrue(rs.next());
        assertEquals(LocalTime.MIN, rs.getObject(1, LocalTime.class));
        assertTrue(rs.next());
        assertEquals(LocalTime.of(23, 59, 59, 999999000), rs.getObject(1, LocalTime.class));
        assertTrue(rs.next());
        assertEquals(LocalTime.MIN, rs.getObject(1, LocalTime.class));
      }
    }
  }

  /**
   * Test the behavior getObject for time columns with null.
   */
  @Test
  public void testGetLocalTimeNull() throws SQLException {
    Statement stmt = con.createStatement();
    stmt.executeUpdate(TestUtil.insertSQL("table1","time_without_time_zone_column","NULL"));

    ResultSet rs = stmt.executeQuery(TestUtil.selectSQL("table1", "time_without_time_zone_column"));
    try {
      assertTrue(rs.next());
      assertNull(rs.getObject("time_without_time_zone_column", LocalTime.class));
      assertNull(rs.getObject(1, LocalTime.class));
    } finally {
      rs.close();
    }
  }

  /**
   * Test the behavior getObject for time columns with null.
   */
  @Test
  public void testGetLocalTimeInvalidType() throws SQLException {
    Statement stmt = con.createStatement();
    stmt.executeUpdate(TestUtil.insertSQL("table1","time_with_time_zone_column", "TIME '04:05:06.123456-08:00'"));

    ResultSet rs = stmt.executeQuery(TestUtil.selectSQL("table1", "time_with_time_zone_column"));
    try {
      assertTrue(rs.next());
      try {
        rs.getObject("time_with_time_zone_column", LocalTime.class);
        fail("Retrieving a LocalTime from a 'time with timezone' column should fail");
      } catch (SQLException e) {
//        assertTrue(e.getSQLState().equals(PSQLState.DATA_TYPE_MISMATCH.getState())
//                || e.getSQLState().equals(PSQLState.BAD_DATETIME_FORMAT.getState()));
      }
      try {
        assertNull(rs.getObject(1, LocalTime.class));
        fail("Retrieving a LocalTime from a 'time with timezone' column should fail");
      } catch (SQLException e) {
//        assertTrue(e.getSQLState().equals(PSQLState.DATA_TYPE_MISMATCH.getState())
//                || e.getSQLState().equals(PSQLState.BAD_DATETIME_FORMAT.getState()));
      }
    } finally {
      rs.close();
    }
  }

  /**
   * Test the behavior getObject for timestamp columns.
   */
  @Test
  public void testGetLocalDateTime() throws SQLException {

    List<String> zoneIdsToTest = new ArrayList<String>();
    zoneIdsToTest.add("Africa/Casablanca"); // It is something like GMT+0..GMT+1
    zoneIdsToTest.add("America/Adak"); // It is something like GMT-10..GMT-9
    zoneIdsToTest.add("Atlantic/Azores"); // It is something like GMT-1..GMT+0
    zoneIdsToTest.add("Europe/Moscow"); // It is something like GMT+3..GMT+4 for 2000s
    zoneIdsToTest.add("Pacific/Apia"); // It is something like GMT+13..GMT+14
    zoneIdsToTest.add("Pacific/Niue"); // It is something like GMT-11..GMT-11
    for (int i = -12; i <= 13; i++) {
      zoneIdsToTest.add(String.format("GMT%+02d", i));
    }

    List<String> datesToTest = Arrays.asList("2015-09-03T12:00:00", "2015-06-30T23:59:58",
            "1997-06-30T23:59:59", "1997-07-01T00:00:00", "2012-06-30T23:59:59", "2012-07-01T00:00:00",
            "2015-06-30T23:59:59", "2015-07-01T00:00:00", "2005-12-31T23:59:59", "2006-01-01T00:00:00",
            "2008-12-31T23:59:59", "2009-01-01T00:00:00", /* "2015-06-30T23:59:60", */ "2015-07-31T00:00:00",
            "2015-07-31T00:00:01", "2015-07-31T00:00:00.000001",

            // On 2000-03-26 02:00:00 Moscow went to DST, thus local time became 03:00:00
            "2000-03-26T01:59:59", "2000-03-26T02:00:00", "2000-03-26T02:00:01", "2000-03-26T02:59:59",
            "2000-03-26T03:00:00", "2000-03-26T03:00:01", "2000-03-26T03:59:59", "2000-03-26T04:00:00",
            "2000-03-26T04:00:01", "2000-03-26T04:00:00.000001",

            // On 2000-10-29 03:00:00 Moscow went to regular time, thus local time became 02:00:00
            "2000-10-29T01:59:59", "2000-10-29T02:00:00", "2000-10-29T02:00:01", "2000-10-29T02:59:59",
            "2000-10-29T03:00:00", "2000-10-29T03:00:01", "2000-10-29T03:59:59", "2000-10-29T04:00:00",
            "2000-10-29T04:00:01", "2000-10-29T04:00:00.000001");

    for (String zoneId : zoneIdsToTest) {
      ZoneId zone = ZoneId.of(zoneId);
      for (String date : datesToTest) {
        localTimestamps(zone, date);
      }
    }
  }

  public void localTimestamps(ZoneId zoneId, String timestamp) throws SQLException {
    TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
    Statement stmt = con.createStatement();
    try {
      stmt.executeUpdate(TestUtil.insertSQL("table1","timestamp_without_time_zone_column","TIMESTAMP '" + timestamp + "'"));

      ResultSet rs = stmt.executeQuery(TestUtil.selectSQL("table1", "timestamp_without_time_zone_column"));
      try {
        assertTrue(rs.next());
        LocalDateTime localDateTime = LocalDateTime.parse(timestamp);
        assertEquals("Failed for " + timestamp + " in " + zoneId, localDateTime, rs.getObject("timestamp_without_time_zone_column", LocalDateTime.class));
        assertEquals("Failed for " + timestamp + " in " + zoneId, localDateTime, rs.getObject(1, LocalDateTime.class));

        //Also test that we get the correct values when retrieving the data as LocalDate objects
        assertEquals(localDateTime.toLocalDate(), rs.getObject("timestamp_without_time_zone_column", LocalDate.class));
        assertEquals(localDateTime.toLocalDate(), rs.getObject(1, LocalDate.class));
      } finally {
        rs.close();
      }
      stmt.executeUpdate("DELETE FROM table1");
    } finally {
      stmt.close();
    }
  }

  /**
   * Test the behavior getObject for timestamp with time zone columns.
   */
  @Test
  public void testGetTimestampWithTimeZone() throws SQLException {
    runGetOffsetDateTime(UTC);
    runGetOffsetDateTime(GMT03);
    runGetOffsetDateTime(GMT05);
    runGetOffsetDateTime(GMT13);
  }

  private void runGetOffsetDateTime(ZoneOffset offset) throws SQLException {
    Statement stmt = con.createStatement();
    try {
      stmt.executeUpdate(TestUtil.insertSQL("table1","timestamp_with_time_zone_column","TIMESTAMP WITH TIME ZONE '2004-10-19 10:23:54.123456" + offset.toString() + "'"));

      ResultSet rs = stmt.executeQuery(TestUtil.selectSQL("table1", "timestamp_with_time_zone_column"));
      try {
        assertTrue(rs.next());
        LocalDateTime localDateTime = LocalDateTime.of(2004, 10, 19, 10, 23, 54, 123456000);

        OffsetDateTime offsetDateTime = localDateTime.atOffset(offset).withOffsetSameInstant(ZoneOffset.UTC);
        assertEquals(offsetDateTime, rs.getObject("timestamp_with_time_zone_column", OffsetDateTime.class));
        assertEquals(offsetDateTime, rs.getObject(1, OffsetDateTime.class));
      } finally {
        rs.close();
      }
      stmt.executeUpdate("DELETE FROM table1");
    } finally {
      stmt.close();
    }
  }

}
