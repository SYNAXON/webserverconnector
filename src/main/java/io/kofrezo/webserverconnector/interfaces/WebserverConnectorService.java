package io.kofrezo.webserverconnector.interfaces;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import java.io.InputStream;
import java.util.List;

/**
 * Webserver Connector Service Interface
 *
 * Interface to offer setup a standard way to setup several concrete webservers such as Apache and NGINX.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @created Aug 8, 2015
 */
public interface WebserverConnectorService {

    public static final String RESOURCE_TYPE_STYLESHEET = "css";
    public static final String RESOURCE_TYPE_JAVASCRIPT = "js";
    public static final String RESOURCE_TYPE_IMAGE = "img";
    public static final String RESOURCE_TYPE_OTHER = "data";

    public static final String DOMAIN_FILTER_ALL = "all";
    public static final String DOMAIN_FILTER_ENABLED = "enabled";
    public static final String DOMAIN_FILTER_DISABLED = "disabled";

    public static final String RESOURCE_IMG_FOLDER = "/var/www/cmf_images";

    /**
     * Get Domains
     *
     * Return array of available domains
     *
     * @param filter
     * @return domains
     */
    public String[] getDomains(String filter);

    /**
     * Create New Domain
     *
     * Create new domain - do nothing if already available
     *
     * @param domain
     * @param aliases
     */
    public void createDomain(String domain, String[] aliases);

    /**
     * Delete Domain
     *
     * Delete domain with given name - do nothing if no available
     *
     * @param domain
     */
    public void deleteDomain(String domain);

    /**
     * Enable Domain
     *
     * Enable domain with given name - do nothing if already enabled
     *
     * @param domain
     */
    public void enableDomain(String domain);

    /**
     * Disable Domain
     *
     * Disable domain with given name - do nothing if already disabled
     *
     * @param domain
     */
    public void disableDomain(String domain);

     /**
     * Get Resources
     *
     * Return array of available resource for domain and type if type is null
     * return all types if domain is null return all domains.
     *
     * @param domain
     * @param type
     * @return resource list
     */
    public String[] getResources(String domain, String type);

     /**
     * Returns a list of available resources for the given domain.
     *
     * @param domain the domain for which the resources should be detected
     * @return a list of available resources for the given domain
     */
    List<String> getResources(final String domain);

    /**
     * Upload Resource
     *
     * @param domain
     * @param type
     * @param src
     * @param dstName
     * @throws com.jcraft.jsch.JSchException
     * @throws com.jcraft.jsch.SftpException
     */
    public void createResource(String domain, String type, String src, String dstName) throws JSchException,
            SftpException;

    /**
     * Uploads a webserver resource to given uploadPath (optional) beginning from the root directory of the
     * given domain.
     *
     * @param domain the domain for which the webserver resource should be uploaded
     * @param src the src of the webserver resource as an InputStream object
     * @param uploadPath the upload path
     * @param dstName the name of the webserver resource
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void createWebserverResource(final String domain, final InputStream src, final String uploadPath,
            final String dstName) throws JSchException, SftpException;

    /**
     * This methods creates an image file on the webserver. All images should be stored in a global directory.
     *
     * @param src
     * @param dstName
     * @throws com.jcraft.jsch.JSchException
     * @throws com.jcraft.jsch.SftpException
     */
    public void createImage(InputStream src, String dstName) throws JSchException, SftpException;

    /**
     * Upload Resource
     *
     * @param domain
     * @param type
     * @param src
     * @param dstName
     * @throws com.jcraft.jsch.JSchException
     * @throws com.jcraft.jsch.SftpException
     */
    void createResource(String domain, String type, InputStream src, String dstName) throws JSchException,
            SftpException;

    /**
     * Delete Resource
     *
     * @param domain
     * @param type
     * @param name
     * @throws com.jcraft.jsch.JSchException
     * @throws com.jcraft.jsch.SftpException
     */
    public void deleteResource(String domain, String type, String name) throws JSchException, SftpException;

    /**
     * Deletes a webserver resource from the given domain.
     *
     * @param domain the domain for which the webserver resource should be uploaded
     * @param name the name of the webserver resource
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void deleteWebserverResource(final String domain, final String name) throws JSchException, SftpException;

    /**
     * Copies the resources from the the source domain to the destination domain.
     *
     * @param sourceDomain the domain the resources to be copied from
     * @param destinationDomain the domain the resources to be copied to
     */
    public void copyResources(final String sourceDomain, final String destinationDomain);

    /**
     * Copies a single resource from the the source domain to the destination domain.
     *
     * @param sourceDomain the domain the resources to be copied from
     * @param destinationDomain the domain the resources to be copied to
     * @param type the mimetype of the ressources
     * @param resourceName the name of the ressource to copy
     */
    void copySingleResource(final String sourceDomain, final String destinationDomain, final String type,
            final String resourceName);

    /**
     *
     * @param domain
     * @param name
     * @return
     */
    InputStream readWebserverResource(final String domain, final String name) throws JSchException, SftpException;
}
