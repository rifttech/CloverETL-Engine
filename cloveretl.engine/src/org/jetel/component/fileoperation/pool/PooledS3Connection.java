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
package org.jetel.component.fileoperation.pool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.jetel.component.fileoperation.PrimitiveS3OperationHandler;
import org.jetel.component.fileoperation.URIUtils;
import org.jetel.graph.ContextProvider;
import org.jetel.graph.runtime.IAuthorityProxy;
import org.jetel.graph.runtime.IGuiAuthorityProxy;
import org.jetel.util.protocols.ProxyConfiguration;
import org.jetel.util.protocols.URLValidator;
import org.jetel.util.protocols.Validable;
import org.jetel.util.protocols.amazon.S3Utils;
import org.jetel.util.string.StringUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerConfiguration;

/**
 * @author krivanekm (info@cloveretl.com)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created 18. 3. 2015
 */
public class PooledS3Connection extends AbstractPoolableConnection implements Validable, URLValidator {

	/**
	 * Shared instance, do not modify!
	 */
	private static final ClientConfiguration DEFAULTS = PredefinedClientConfigurations.defaultConfig();
	
	/**
	 * Shared instance for all connections to the same authority,
	 * should be thread-safe.
	 */
	private AmazonS3Client service;
	
	/**
	 * s3://access_key_id:secret_access_key@hostname:port/
	 * 
	 * - hostname does not include bucket name
	 */
	private URI baseUri;

	/**
	 * @param authority
	 */
	protected PooledS3Connection(S3Authority authority) {
		super(authority);
		
		URI uri = authority.getPlainUri();
		StringBuilder sb = new StringBuilder();
		sb.append(uri.getScheme()).append(":");
		String proxyString = authority.getProxyString();
		if (!StringUtils.isEmpty(proxyString)) {
			sb.append('(').append(proxyString).append(')');
		}
		sb.append("//").append(uri.getRawAuthority()).append('/'); // do not decode escape sequences
		this.baseUri = URI.create(sb.toString());
	}
	
	@Override
	public boolean isOpen() {
		// TODO perform a connection test request?
		return (service != null);
	}
	
	public void init() throws IOException {
		this.service = createService((S3Authority) authority);
		validate();
	}
	
	@Override
	public void validate() throws IOException {
		// Validation is not necessary, because AmazonS3Client uses HTTP connection pooling internally.
		// A connection with invalid credentials can get into our connection pool, but it doesn't matter.
		// The first request attempt with invalid connection will fail.
		// The connection will be automatically removed from the pool after 1 minute of inactivity.
		// Connection validation in File URL dialog uses validate(URL) instead.
	}

	@Override
	public void validate(URL url) throws IOException {
		try {
			// we assume that the exception thrown from listFiles() will contain a meaningful error message
			PrimitiveS3OperationHandler.listFiles(url.toURI(), this);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void close() throws IOException {
		IOException ioe = null;
		if (service != null) {
			try {
				service.shutdown();
			} catch (Exception e) {
				ioe = S3Utils.getIOException(e);
			} finally {
				this.service = null;
				if (transferManager != null) {
					try {
						transferManager.shutdownNow(false);
					} catch (Exception e) {
						if (ioe != null) {
							ioe.addSuppressed(e);
						} else {
							ioe = S3Utils.getIOException(e);
						}
					}
				}
			}
		}
		if (ioe != null) {
			throw ioe;
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			if (isBorrowed()) {
				// CLO-6688: leaked connections must be closed, as they may be already broken
				close();
			}
		} finally {
			super.finalize();
		}
	}

	public static String getAccessKey(S3Authority authority) {
		String userinfo = authority.getPlainUri().getRawUserInfo();
		if (userinfo == null) {
			return "";
		}
		
		int colonPos = userinfo.indexOf(':');
		
		String accessKey = (colonPos != -1) ? userinfo.substring(0, colonPos) : userinfo;
		return URIUtils.urlDecode(accessKey);
	}
	
	public static String getSecretKey(S3Authority authority) {
		String userinfo = authority.getPlainUri().getRawUserInfo();
		if (userinfo == null) {
			return "";
		}
		
		int colonPos = userinfo.indexOf(':');
		
		if (colonPos != -1) {
			return URIUtils.urlDecode(userinfo.substring(colonPos + 1));
		} else {
			return "";
		}
	}

	private AmazonS3Client createService(S3Authority authority) {
		String accessKey = getAccessKey(authority);
		String secretKey = getSecretKey(authority);

		AWSCredentials credentials = null;
		if (!StringUtils.isEmpty(accessKey)) { // CLO-13093
			credentials = new BasicAWSCredentials(accessKey, secretKey);
		}
		
		ClientConfiguration properties = new ClientConfiguration(DEFAULTS);
		
		// CLO-10185: proxy support
		ProxyConfiguration proxyConfiguration = new ProxyConfiguration(authority.getProxyString());
		if (proxyConfiguration.isProxyUsed()) {
			properties.setProxyHost(proxyConfiguration.getHost());
			int port = proxyConfiguration.getPort();
			if (port > -1) {
				properties.setProxyPort(port);
			}
			properties.setProxyUsername(proxyConfiguration.getProxyUser());
			properties.setProxyPassword(proxyConfiguration.getProxyPassword());
		} else if (proxyConfiguration.getProxy() == Proxy.NO_PROXY) {
			properties.setNonProxyHosts("*"); // disable proxies for all hostnames, see http.nonProxyHosts system property
		}
		
//		properties.setSignerOverride("AWSS3V4SignerType");
		AmazonS3Client amazonS3Client;
		if (credentials != null) {
			amazonS3Client = new AmazonS3Client(credentials, properties); // static credentials
		} else { // CLO-13093:
			// FIXME currently, pooled connections may use a credentials provider from another Server instance;
			// this may cause in Designer that the user can browse buckets that are not accessible from the current Server project 

			// create a new chain, AWSCredentialsProviderChain saves last used provider and it may be called from a different project
			AWSCredentialsProvider credentialsProvider = new CredentialsProviderChain(GuiCredentialsProvider.INSTANCE, S3Utils.getDefaultCredentialsProvider());
			amazonS3Client = new AmazonS3Client(credentialsProvider, properties); // default credentials providers
		}
		amazonS3Client.setEndpoint(authority.getHost());
		
		return amazonS3Client;
	}
	
	/**
	 * Returns the {@link AmazonS3} instance.
	 *  
	 * @return {@link AmazonS3}
	 */
	public AmazonS3 getService() {
		return service;
	}
	
	private TransferManager transferManager;
	
	public TransferManager getTransferManager() {
		if (transferManager == null) {
			transferManager = new TransferManager(service);
			TransferManagerConfiguration config = new TransferManagerConfiguration();
//			config.setMultipartUploadThreshold(MULTIPART_UPLOAD_THRESHOLD);
//			config.setMinimumUploadPartSize(MULTIPART_UPLOAD_THRESHOLD);
			transferManager.setConfiguration(config);
		}
		return transferManager;
	}
	
	/**
	 * Returns the base URI of the connection.
	 * 
	 * @return base URI
	 */
	public URI getBaseUri() {
		return baseUri;
	}
	
	/**
	 * Returns an {@link InputStream} for reading from the specified {@link URI}.
	 * <p>
	 * <b>Calling this method passes ownership of the connection to the stream.</b>
	 * </p>
	 * 
	 * @param uri - source {@link URI}
	 * @return new {@link InputStream} that takes ownership of the connection
	 * @throws IOException
	 */
	public InputStream getInputStream(URI uri) throws IOException {
		return S3Utils.getInputStream(uri, this);
	}

	/**
	 * Returns an {@link OutputStream} instance that writes data
	 * to a temp file, uploads it to S3 when the stream is closed
	 * and deletes the temp file.
	 * 
	 * If the file size exceeds 5 GB, performs multipart upload.
	 * <p>
	 * <b>Calling this method passes ownership of the connection to the stream.</b>
	 * </p>
	 * 
	 * @param uri - target {@link URI}
	 * @return new {@link OutputStream} that takes ownership of the connection
	 * @throws IOException
	 */
	public OutputStream getOutputStream(URI uri) throws IOException {
		return PrimitiveS3OperationHandler.getOutputStream(uri, this);
	}
	
	private static class CredentialsProviderChain extends AWSCredentialsProviderChain {

		public CredentialsProviderChain(AWSCredentialsProvider... providers) {
			super(providers);
		}

		@Override
		public AWSCredentials getCredentials() {
            try {
                return super.getCredentials();
            } catch (AmazonClientException ace) {
                return null; // fall back to anonymous
            }
		}
		
	}
	
	/**
	 * CLO-13093:
	 * 
	 * Returns {@link AWSCredentials} from GuiAuthorityProxy registered via {@link ContextProvider}.
	 * This allows us to load default credentials from CloverDX Server in remote projects.
	 * 
	 * @author krivanekm (info@cloveretl.com)
	 *         (c) Javlin, a.s. (www.cloveretl.com)
	 *
	 * @created 10. 4. 2018
	 */
	private static class GuiCredentialsProvider implements AWSCredentialsProvider {

		private static final GuiCredentialsProvider INSTANCE = new GuiCredentialsProvider();

		@Override
		public AWSCredentials getCredentials() {
			AWSCredentials result = null;
			
			AWSCredentialsProvider delegate = getProjectCredentialsProvider();
			if (delegate != null) {
				result = delegate.getCredentials();
			}
			
			if (result == null) {
				result = S3Utils.NULL_CREDENTIALS; // used in a chain, prevent NPE
			}
			
			return result;
		}
		
		@Override
		public void refresh() {
			AWSCredentialsProvider delegate = getProjectCredentialsProvider();
			if (delegate != null) {
				delegate.refresh();
			}
		}
		
		private AWSCredentialsProvider getProjectCredentialsProvider() {
			AWSCredentialsProvider result = null;
			
        	IAuthorityProxy authorityProxy = ContextProvider.getAuthorityProxy();
        	if (authorityProxy instanceof IGuiAuthorityProxy) {
        		IGuiAuthorityProxy guiAuthorityProxy = (IGuiAuthorityProxy) authorityProxy;
        		result = guiAuthorityProxy.getCredentialsProvider();
        	}
        	
        	return result;
		}

	}
	
}
