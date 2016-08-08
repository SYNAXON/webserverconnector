package io.kofrezo.webserverconnector.interfaces;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import java.io.InputStream;
import java.util.List;

/**
 * Interface to offer setup a standard way to setup several concrete webservers such as Apache and NGINX.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @author Jan Nurmse
 */
public interface WebserverConnectorService {

    String RESOURCE_TYPE_STYLESHEET = "css";
    String RESOURCE_TYPE_JAVASCRIPT = "js";
    String RESOURCE_TYPE_IMAGE = "img";
    String RESOURCE_TYPE_OTHER = "data";

    String DOMAIN_FILTER_ALL = "all";
    String DOMAIN_FILTER_ENABLED = "enabled";
    String DOMAIN_FILTER_DISABLED = "disabled";

    String RESOURCE_IMG_FOLDER = "/var/www/cmf_images";

    /**
     * Returns an array of available domains.
     *
     * @param filter filter domain mode
     * @return domains array of available domains
     */
    String[] getDomains(final String filter);

    /**
     * Creates a new domain - do nothing if already available.
     *
     * @param domain the name of the new domain
     */
    void createDomain(final String domain);

    /**
     * Deletes a domain with given name - do nothing if no available.
     *
     * @param domain the name of the domain that should be deleted
     */
    void deleteDomain(final String domain);

    /**
     * Enables a domain with given name - do nothing if already enabled.
     *
     * @param domain the name of the domain that should be enabled
     */
    void enableDomain(final String domain);

    /**
     * Disables a domain with given name - do nothing if already disabled.
     *
     * @param domain the name of the domain that should be disabled
     */
    void disableDomain(final String domain);

     /**
     * Returns an array of available resource for the given domain and type. If type is null the method
     * returns all types. If domain is null the method returns all domains.
     *
     * @param domain the domain for which the resources should be detected
     * @param type resources can be filterery by type
     * @return an aary of available resources for the given domain
     */
    String[] getResources(final String domain, final String type);

     /**
     * Returns a list of available resources for the given domain.
     *
     * @param domain the domain for which the resources should be detected
     * @return a list of available resources for the given domain
     */
    List<String> getResources(final String domain);

    /**
     * Uploads a resource.
     *
     * @param domain the domain for which the resource should be uploaded
     * @param type the type of the new resource
     * @param src the src of the resource
     * @param dstName the name of the resource
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void createResource(final String domain, final String type, final String src, final String dstName)
            throws JSchException, SftpException;

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
     * @param src the src of the image file as an InputStream object
     * @param dstName the name of timage file
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void createImage(final InputStream src, final String dstName) throws JSchException, SftpException;

    /**
     * Uploads a resource.
     *
     * @param domain the domain for which the resource should be uploaded
     * @param type the type of the new resource
     * @param src the src of the resource as an InputStream object
     * @param dstName the name of the resource
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void createResource(final String domain, final String type, final InputStream src, final String dstName)
            throws JSchException, SftpException;

    /**
     * Deletes a resource.
     *
     * @param domain the domain for which the resource should be deleted
     * @param type the type of the resource
     * @param name the name of the resource
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    void deleteResource(final String domain, final String type, final String name)
            throws JSchException, SftpException;

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
    void copyResources(final String sourceDomain, final String destinationDomain);

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
     * Returns an InputStream for a webserver resource.
     *
     * @param domain the domain for which the webserver resource should be uploaded
     * @param name the name of the webserver resource
     * @return an InputStream for a webserver resource
     * @throws JSchException exception will be thrown if anything goes wrong with the SSH protocol
     * @throws SftpException exception will be thrown if anything goes wrong while using the SFTP protocol
     */
    InputStream readWebserverResource(final String domain, final String name)
            throws JSchException, SftpException;
}
