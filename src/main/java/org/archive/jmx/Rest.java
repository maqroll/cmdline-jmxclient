package org.archive.jmx;

import java.util.HashMap;
import java.util.List;

import javax.ws.rs.QueryParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jetty.server.Server;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

@Path("jmx")
public class Rest {
	private Server jettyServer;

	private HashMap<String,Values> values;
	
	public Rest(HashMap<String,Values> values) {
		this.values = values;
	}

	private static final int PORT = 9090;
	
	@GET
	@Path("/{id}")
	@Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
	public Response getStock(@PathParam("id") final String key, @QueryParam("start") final long start, @QueryParam("stop") final long stop) {
		StringBuffer r = new StringBuffer();
		r.append("[");
		Values vls = values.get(key);
		if (vls != null) {
			List<ValueBean> l = vls.get(start, stop);
			
			for (int i = 0; i < l.size(); i++) {
				ValueBean v = l.get(i);
				if (i > 0) r.append(",");
				r.append("{");
				r.append("\"MSeconds\":" +v.MSeconds);
				r.append(",\"Value\":" +v.Value);
				r.append("}");
			}
		}
		r.append("]");
		
		      Response.ResponseBuilder rb = Response.ok(r.toString());
		      Response response = rb.header("Access-Control-Allow-Origin","*")
		                            .build();
		      return response;
	}

	public void start() throws Exception {
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");

		jettyServer = new Server(PORT);
		jettyServer.setHandler(context);

		ResourceConfig rc = new ResourceConfig();
		rc.register(this); 

		ServletContainer sc = new ServletContainer(rc);
		ServletHolder holder = new ServletHolder(sc);
		context.addServlet(holder, "/*");

		jettyServer.start();
	}

	public void stop() throws Exception {
		jettyServer.stop();

	}
}
