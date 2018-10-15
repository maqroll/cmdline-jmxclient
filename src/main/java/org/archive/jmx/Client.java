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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
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
    private static JMXConnector jmxc;

    private static final long SLEEP_TIME = 1000;
    
    private static final int PARALLEL = 5;
    
    private static final String SEPARATOR = "@";
    
    private static HashMap<String,Values> values = new HashMap<String,Values>();
    
    /**
     * Usage string.
     */
    private static final String USAGE = "TODO";
    
    /**
     * Pattern that matches a command name followed by
     * an optional equals and optional comma-delimited list
     * of arguments.
     */
    protected static final Pattern CMD_LINE_ARGS_PATTERN =
        Pattern.compile("^([^=]+)(?:(?:\\=)(.+))?$");
    
	public static void main(String[] args) throws Exception {
        Client client = new Client();
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
    protected Map<String,String[]> formatCredentials(final String login,
            final String password) {
        Map<String,String[]> env = null;
        String[] creds = new String[] {login, password};
        env = new HashMap<String,String[]>(1);
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
        String [] loginPassword = parseUserpass(userpass);

        /* Init */
    	for (String c : command) {
    		if (c.split(SEPARATOR)[0].contains("*") /* Wildcards on the name */) {
    			String prop = c.split(SEPARATOR)[1];
    			for(String a : resolveWildcards(c,getJMXConnector(hostport,((loginPassword == null)? null: loginPassword[0]),((loginPassword == null)? null: loginPassword[1])))) {
    				values.put(a + SEPARATOR + prop, new Values());
    			}
    		} else {
    			values.put(c, new Values());
    		}
    	}
    	
        new Rest(values).start();
        
        String[] keys = values.keySet().toArray(new String[]{});
        final int p = (keys.length < PARALLEL) ? keys.length : PARALLEL;
    	final int step = keys.length/p;

    	for (int i = 0; i < p; i++) {
        	final int from = i * step;
        	final int to = (i != (p-1))? (i+1)*step : keys.length; 

        	new Thread(() -> {
        		while (true) {
        			try {
        		        execute(hostport,
        			            ((loginPassword == null)? null: loginPassword[0]),
        			            ((loginPassword == null)? null: loginPassword[1]), beanname,
        			            Arrays.copyOfRange(keys, from, to));
        		        Thread.sleep(SLEEP_TIME);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (Exception e) {
						e.printStackTrace();
					}
        		}
        	}) .start();
        }  
        
        Thread.currentThread().join(); /* wait forever */
    }
    
    private List<String> resolveWildcards(String c,JMXConnector connector) {
    	List<String> result = new ArrayList<String>();
    	try {
			Set<ObjectName> queryNames = connector.getMBeanServerConnection().queryNames(new ObjectName(c.split(SEPARATOR)[0]), null);
			for (ObjectName n : queryNames) {
				result.add(n.getCanonicalName());
			}
		} catch (MalformedObjectNameException | IOException e) {
			System.err.println("Failed to resolve: " + c);
			e.printStackTrace();
		}
    	return result;
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
        		String[] p = c.split(SEPARATOR);
        		try {
        			Object r = conn.getAttribute(new ObjectName(p[0]), p[1]);
            		values.get(c).add(r.toString());
                } catch (AttributeNotFoundException | InstanceNotFoundException | MalformedObjectNameException | MBeanException
        				| ReflectionException | IOException e) {
                	System.err.println("Failed to get value for:" + c);
        			e.printStackTrace();
                }
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
    
}
