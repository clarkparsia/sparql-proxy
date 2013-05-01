package com.clarkparsia.sparql;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Sparql Endpoint Proxy Servlet - Helps for bypassing CORS issues.
 * 
 * Simple proxy that redirects all elements in the request to a configured SPARQL endpoint.
 * Headers in the request are also redirected.
 * 
 * @author Edgar Rodriguez
 * @version 0.0.1-beta
 */
public class SparqlEndpointProxy extends HttpServlet {

	/**
	 * Auto generated serialVersionUID
	 */
	private static final long serialVersionUID = 4572026977285618250L;
	private Log log = LogFactory.getLog(SparqlEndpointProxy.class);

	public void doGet(HttpServletRequest theReq, HttpServletResponse theResp) throws ServletException, IOException {
		String aSparqlEndpointQuery = theReq.getParameter("query");
		
		if (log.isDebugEnabled()) log.debug(aSparqlEndpointQuery);

		try {
			buildSparqlResponse(theReq, theResp);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void buildSparqlResponse(HttpServletRequest theReq, HttpServletResponse theResp) throws Exception {
		HttpClient httpclient = new DefaultHttpClient();
		HttpGet httpget = null;
		HttpResponse response = null;

		String theServerHost = getServletConfig().getInitParameter("proxy.host");
		String aDBRoute = theReq.getPathInfo();
		String aEncodedQuery = URLEncoder.encode(theReq.getParameter("query"), "UTF-8");

		String theReqUrl = theServerHost + aDBRoute + "?query=" + aEncodedQuery;
		if (log.isDebugEnabled()) log.debug("> Sparql-proxy: "+ theReqUrl); 

		try {
			httpget = new HttpGet(theReqUrl);

			Enumeration<String> aHeadersEnum = theReq.getHeaderNames();
			while (aHeadersEnum.hasMoreElements()) {
				String aHeaderName = aHeadersEnum.nextElement();
				String aHeaderVal = theReq.getHeader(aHeaderName);

				httpget.setHeader(aHeaderName, aHeaderVal);
			}

			if (log.isDebugEnabled()) log.debug("executing request " + httpget.getURI());

			// Create a response handler
			response = httpclient.execute(httpget);

			// set the same Headers
			for(Header aHeader : response.getAllHeaders()) {
				theResp.setHeader(aHeader.getName(), aHeader.getValue());
			}

			// set the same locale
			theResp.setLocale(response.getLocale());

			// set the content
			theResp.setContentLength((int) response.getEntity().getContentLength());
			theResp.setContentType(response.getEntity().getContentType().getValue());

			// set the same status
			theResp.setStatus(response.getStatusLine().getStatusCode());

			// redirect the output
			InputStream aInStream = null;
			OutputStream aOutStream = null;
			try {
				aInStream = response.getEntity().getContent();
				aOutStream = theResp.getOutputStream();

				int data = aInStream.read();
				while(data != -1) {
					aOutStream.write(data);

					data = aInStream.read();
				}
			}
			catch(IOException ioe) {
				ioe.printStackTrace();
			}
			finally {
				if (aInStream != null)
					aInStream.close();
				if (aOutStream != null) 
					aOutStream.close();
			}

		} finally {
			httpclient.getConnectionManager().closeExpiredConnections();
			httpclient.getConnectionManager().shutdown();
		}
	}

}