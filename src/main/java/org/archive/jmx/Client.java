/*
 * Client
 *
 * $Id$
 *
 * Created on Nov 12, 2004
 *
 * Copyright (C) 2004 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.jmx;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;


/**
 * A Simple Command-Line JMX Client.
 * Tested against the JDK 1.5.0 JMX Agent.
 * See <a href="http://java.sun.com/j2se/1.5.0/docs/guide/management/agent.html">Monitoring
 * and Management Using JMX</a>.
 * <p>Can supply credentials and do primitive string representation of tabular
 * and composite openmbeans.
 * @author stack
 */
public class Client {
    private static final Logger logger =
        Logger.getLogger(Client.class.getName());
    
    private static JMXConnector jmxc;

    private static final long SLEEP_TIME = 1000;
    
    private static HashMap<String,Values> values = new HashMap<String,Values>();
    
    /**
     * Usage string.
     */
    private static final String USAGE = "Usage: java -jar" +
        " cmdline-jmxclient.jar USER:PASS HOST:PORT [BEAN] [COMMAND]\n" +
        "Options:\n" +
        " USER:PASS Username and password. Required. If none, pass '-'.\n" +
        "           E.g. 'controlRole:secret'\n" +
        " HOST:PORT Hostname and port to connect to. Required." +
        " E.g. localhost:8081.\n" +
        "           Lists registered beans if only USER:PASS and this" +
        " argument.\n" +
        " BEAN      Optional target bean name. If present we list" +
        " available operations\n" +
        "           and attributes.\n" +
        " COMMAND   Optional operation to run or attribute to fetch. If" +
        " none supplied,\n" +
        "           all operations and attributes are listed. Attributes" +
        " begin with a\n" +
        "           capital letter: e.g. 'Status' or 'Started'." +
        " Operations do not.\n" +
        "           Operations can take arguments by adding an '=' " +
        "followed by\n" +
        "           comma-delimited params. Pass multiple " +
        "attributes/operations to run\n" +
        "           more than one per invocation. Use commands 'create' and " +
        "'destroy'\n" +
        "           to instantiate and unregister beans ('create' takes name " +
        "of class).\n" +
        "           Pass 'Attributes' to get listing of all attributes and " +
        "and their\n" +
        "           values.\n" +
        "Requirements:\n" +
        " JDK1.5.0. If connecting to a SUN 1.5.0 JDK JMX Agent, remote side" +
        " must be\n" +
        " started with system properties such as the following:\n" +
        "     -Dcom.sun.management.jmxremote.port=PORT\n" +
        "     -Dcom.sun.management.jmxremote.authenticate=false\n" +
        "     -Dcom.sun.management.jmxremote.ssl=false\n" +
        " The above will start the remote server with no password. See\n" +
        " http://java.sun.com/j2se/1.5.0/docs/guide/management/agent.html" +
        " for more on\n" +
        " 'Monitoring and Management via JMX'.\n" +
        "Client Use Examples:\n" +
        " To list MBeans on a non-password protected remote agent:\n" +
        "     % java -jar cmdline-jmxclient-X.X.jar - localhost:8081 \\\n" +
        "         org.archive.crawler:name=Heritrix,type=Service\n" +
        " To list attributes and attributes of the Heritrix MBean:\n" +
        "     % java -jar cmdline-jmxclient-X.X.jar - localhost:8081 \\\n" +
        "         org.archive.crawler:name=Heritrix,type=Service \\\n" +
        "         schedule=http://www.archive.org\n" +
        " To set set logging level to FINE on a password protected JVM:\n" +
        "     % java -jar cmdline-jmxclient-X.X.jar controlRole:secret" +
        " localhost:8081 \\\n" +
        "         java.util.logging:type=Logging \\\n" +
        "         setLoggerLevel=org.archive.crawler.Heritrix,FINE";
    
    /**
     * Pattern that matches a command name followed by
     * an optional equals and optional comma-delimited list
     * of arguments.
     */
    protected static final Pattern CMD_LINE_ARGS_PATTERN =
        Pattern.compile("^([^=]+)(?:(?:\\=)(.+))?$");
    
    private static final String CREATE_CMD_PREFIX = "create=";
    
	public static void main(String[] args) throws Exception {
        Client client = new Client();
        // Set the logger to use our all-on-one-line formatter.
        Logger l = Logger.getLogger("");
        Handler [] hs = l.getHandlers();
        for (int i = 0; i < hs.length; i++) {
            Handler h = hs[0];
            if (h instanceof ConsoleHandler) {
                h.setFormatter(client.new OneLineSimpleLogger());
            }
        }
        client.execute(args);
	}
    
    protected static void usage() {
        usage(0, null);
    }
    
    protected static void usage(int exitCode, String message) {
        if (message != null && message.length() > 0) {
            System.out.println(message);
        }
        System.out.println(USAGE);
        System.exit(exitCode);
    }

    /**
     * Constructor.
     */
    public Client() {
        super();
    }
    
    /**
     * Parse a 'login:password' string.  Assumption is that no
     * colon in the login name.
     * @param userpass
     * @return Array of strings with login in first position.
     */
    protected String [] parseUserpass(final String userpass) {
        if (userpass == null || userpass.equals("-")) {
            return null;
        }
        int index = userpass.indexOf(':');
        if (index <= 0) {
            throw new RuntimeException("Unable to parse: " +userpass);
        }
        return new String [] {userpass.substring(0, index),
            userpass.substring(index + 1)};
    }
    
    /**
     * @param login
     * @param password
     * @return Credentials as map for RMI.
     */
    protected Map formatCredentials(final String login,
            final String password) {
        Map env = null;
        String[] creds = new String[] {login, password};
        env = new HashMap(1);
        env.put(JMXConnector.CREDENTIALS, creds);
        return env;
    }
    
    protected JMXConnector getJMXConnector(final String hostport,
            final String login, final String password)
    throws IOException {
        // Make up the jmx rmi URL and get a connector.
        JMXServiceURL rmiurl = new JMXServiceURL("service:jmx:rmi://"
            + hostport + "/jndi/rmi://" + hostport + "/jmxrmi");
        return JMXConnectorFactory.connect(rmiurl,
            formatCredentials(login, password));
    }
    
    protected ObjectName getObjectName(final String beanname)
    throws MalformedObjectNameException, NullPointerException {
        return notEmpty(beanname)? new ObjectName(beanname): null;
    }
    
    /**
     * Version of execute called from the cmdline.
     * Prints out result of execution on stdout.
     * Parses cmdline args.  Then calls {@link #execute(String, String,
     * String, String, String[], boolean)}.
     * @param args Cmdline args.
     * @throws Exception
     */
    protected void execute(final String [] args)
    throws Exception {
        // Process command-line.
        if (args.length == 0 || args.length == 1) {
            usage();
        }
        String userpass = args[0];
        String hostport = args[1];
        String beanname = null;
        String [] command = null;
        if (args.length > 2) {
            command = new String [args.length - 2];
            for (int i = 2; i < args.length; i++) {
                command[i - 2] = args[i];
            }
        }
        /*if (args.length > 3) {
            command = new String [args.length - 3];
            for (int i = 3; i < args.length; i++) {
                command[i - 3] = args[i];
            }
        }*/
        String [] loginPassword = parseUserpass(userpass);

        /* Init */
    	for (String c : command) {
    		values.put(c, new Values());
    	}
    	
        new Rest(values).start();
        
    	// main loop
        while (true) {
	        Object [] result = execute(hostport,
	            ((loginPassword == null)? null: loginPassword[0]),
	            ((loginPassword == null)? null: loginPassword[1]), beanname,
	            command);
	        Thread.sleep(SLEEP_TIME);
        }
        
    }
    
    protected Object [] execute(final String hostport, final String login,
            final String password, final String beanname,
            final String [] command)
    throws Exception {
        return execute(hostport, login, password, beanname, command, false);
    }

    public Object [] executeOneCmd(final String hostport, final String login,
            final String password, final String beanname,
            final String command)
    throws Exception {
        return execute(hostport, login, password, beanname,
            new String[] {command}, true);
    }
    
    /**
     * Execute command against remote JMX agent.
     * @param hostport 'host:port' combination.
     * @param login RMI login to use.
     * @param password RMI password to use.
     * @param beanname Name of remote bean to run command against.
     * @param command Array of commands to run.
     * @param oneBeanOnly Set true if passed <code>beanname</code> is
     * an exact name and the query for a bean is only supposed to return
     * one bean instance. If not, we raise an exception (Otherwise, if false,
     * then we deal with possibility of multiple bean instances coming back
     * from query). Set to true when want to get an attribute or run an
     * operation.
     * @return Array of results -- one per command.
     * @throws Exception
     */
    protected Object [] execute(final String hostport, final String login,
            final String password, final String beanname,
            final String [] command, final boolean oneBeanOnly)
    throws Exception {
    	if (jmxc == null) {
    		jmxc = getJMXConnector(hostport, login, password);
    	}
        Object [] result = null;
        
        try {
        	MBeanServerConnection conn = jmxc.getMBeanServerConnection();
        	for(String c : command) {
        		String[] p = c.split("@");
        		Object r = conn.getAttribute(new ObjectName(p[0]), p[1]);
        		values.get(c).add(r.toString());
        	}
        } finally {
            //jmxc.close();
        }
        return result;
    }
    
    protected boolean notEmpty(String s) {
        return s != null && s.length() > 0;
    }
           
    /**
     * Class that parses commandline arguments.
     * Expected format is 'operationName=arg0,arg1,arg2...'. We are assuming no
     * spaces nor comma's in argument values.
     */
    protected class CommandParse {
        private String cmd;
        private String [] args;
        
        protected CommandParse(String command) throws ParseException {
            parse(command);
        }
        
        private void parse(String command) throws ParseException {
            Matcher m = CMD_LINE_ARGS_PATTERN.matcher(command);
            if (m == null || !m.matches()) {
                throw new ParseException("Failed parse of " + command, 0);
            }

            this.cmd = m.group(1);
            if (m.group(2) != null && m.group(2).length() > 0) {
                this.args = m.group(2).split(",");
            } else {
                this.args = null;
            }
        }
        
        protected String getCmd() {
            return this.cmd;
        }
        
        protected String [] getArgs() {
            return this.args;
        }
    }
    
    /**
     * Logger that writes entry on one line with less verbose date.
     * Modelled on the OneLineSimpleLogger from Heritrix.
     * 
     * @author stack
     * @version $Revision$, $Date$
     */
    private class OneLineSimpleLogger extends SimpleFormatter {
        /**
         * Date instance.
         * 
         * Keep around instance of date.
         */
        private Date date = new Date();
        
        /**
         * Field position instance.
         * 
         * Keep around this instance.
         */
        private FieldPosition position = new FieldPosition(0);
        
        /**
         * MessageFormatter for date.
         */
        private SimpleDateFormat formatter =
            new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z");
        
        /**
         * Persistent buffer in which we conjure the log.
         */
        private StringBuffer buffer = new StringBuffer();
        

        public OneLineSimpleLogger() {
            super();
        }
        
        public synchronized String format(LogRecord record) {
            this.buffer.setLength(0);
            this.date.setTime(record.getMillis());
            this.position.setBeginIndex(0);
            //this.formatter.format(this.date, this.buffer, this.position);
            //this.buffer.append(' ');
            //if (record.getSourceClassName() != null) {
            //    this.buffer.append(record.getSourceClassName());
            //} else {
            //    this.buffer.append(record.getLoggerName());
            //}
            //this.buffer.append(' ');
            this.buffer.append(formatMessage(record));
            this.buffer.append(System.getProperty("line.separator"));
            if (record.getThrown() != null) {
                try {
                    StringWriter writer = new StringWriter();
                    PrintWriter printer = new PrintWriter(writer);
                    record.getThrown().printStackTrace(printer);
                    writer.close();
                    this.buffer.append(writer.toString());
                } catch (Exception e) {
                    this.buffer.append("Failed to get stack trace: " +
                        e.getMessage());
                }
            }
            return this.buffer.toString();
        }
    }
}
