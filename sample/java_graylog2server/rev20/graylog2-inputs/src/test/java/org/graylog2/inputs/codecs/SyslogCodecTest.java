/**
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.inputs.codecs;

import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.inputs.codecs.Codec;
import org.graylog2.plugin.journal.RawMessage;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class SyslogCodecTest {
    public static String STRUCTURED = "<165>1 2012-12-25T22:14:15.003Z mymachine.example.com evntslog - ID47 [exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"] BOMAn application event log entry";
    private final String UNSTRUCTURED = "<45>Oct 21 12:09:37 c4dc57ba1ebb syslog-ng[7208]: syslog-ng starting up; version='3.5.3'";

    @Mock private Configuration configuration;

    private Codec codec;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        codec = new SyslogCodec(configuration);
    }

    @Test
    public void testDecodeStructured() throws Exception {
        final Message message = codec.decode(buildRawMessage(STRUCTURED));

        assertEquals(message.getMessage(), "ID47 [exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"] BOMAn application event log entry");
        assertEquals(((DateTime)message.getField("timestamp")).withZone(DateTimeZone.UTC), new DateTime("2012-12-25T22:14:15.003Z", DateTimeZone.UTC));
        assertEquals(message.getField("source"), "mymachine.example.com");
        assertEquals(message.getField("level"), 5);
        assertEquals(message.getField("facility"), "local4");
        assertEquals(message.getField("eventSource"), "Application");
        assertEquals(message.getField("eventID"), "1011");
        assertEquals(message.getField("iut"), "3");
        assertEquals(message.getField("application_name"), "evntslog");
        // Needs https://github.com/Graylog2/graylog2-server/pull/744 to pull out all SD-ELEMENTS.
        //assertEquals(message.getField("sequenceId"), "1");
    }

    @Test
    public void testDecodeStructuredWithFullMessage() throws Exception {
        when(configuration.getBoolean(SyslogCodec.CK_STORE_FULL_MESSAGE)).thenReturn(true);

        final Message message = codec.decode(buildRawMessage(STRUCTURED));

        assertEquals(message.getMessage(), "ID47 [exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"] BOMAn application event log entry");
        assertEquals(((DateTime)message.getField("timestamp")).withZone(DateTimeZone.UTC), new DateTime("2012-12-25T22:14:15.003Z", DateTimeZone.UTC));
        assertEquals(message.getField("source"), "mymachine.example.com");
        assertEquals(message.getField("level"), 5);
        assertEquals(message.getField("facility"), "local4");
        assertEquals(message.getField("full_message"), STRUCTURED);
        assertEquals(message.getField("eventSource"), "Application");
        assertEquals(message.getField("eventID"), "1011");
        assertEquals(message.getField("iut"), "3");
        assertEquals(message.getField("application_name"), "evntslog");
        // Needs https://github.com/Graylog2/graylog2-server/pull/744 to pull out all SD-ELEMENTS.
        //assertEquals(message.getField("sequenceId"), "1");
    }

    @Test
    public void testDecodeUnstructured() throws Exception {
        final Message message = codec.decode(buildRawMessage(UNSTRUCTURED));

        assertEquals(message.getMessage(), "c4dc57ba1ebb syslog-ng[7208]: syslog-ng starting up; version='3.5.3'");
        assertEquals(message.getField("timestamp"), new DateTime(DateTime.now().getYear() + "-10-21T12:09:37"));
        assertEquals(message.getField("source"), "c4dc57ba1ebb");
        assertEquals(message.getField("level"), 5);
        assertEquals(message.getField("facility"), "syslogd");
        assertNull(message.getField("full_message"));
    }

    @Test
    public void testDecodeUnstructuredWithFullMessage() throws Exception {
        when(configuration.getBoolean(SyslogCodec.CK_STORE_FULL_MESSAGE)).thenReturn(true);

        final Message message = codec.decode(buildRawMessage(UNSTRUCTURED));

        assertEquals(message.getMessage(), "c4dc57ba1ebb syslog-ng[7208]: syslog-ng starting up; version='3.5.3'");
        assertEquals(message.getField("timestamp"), new DateTime(DateTime.now().getYear() + "-10-21T12:09:37"));
        assertEquals(message.getField("source"), "c4dc57ba1ebb");
        assertEquals(message.getField("level"), 5);
        assertEquals(message.getField("facility"), "syslogd");
        assertEquals(message.getField("full_message"), UNSTRUCTURED);
    }

    private RawMessage buildRawMessage(String message) {
        return new RawMessage("syslog", "input-id", new InetSocketAddress(5140), message.getBytes());
    }
}