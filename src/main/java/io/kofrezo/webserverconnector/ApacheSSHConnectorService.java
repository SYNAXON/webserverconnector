package io.kofrezo.webserverconnector;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import io.kofrezo.webserverconnector.interfaces.WebserverConnectorService;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Stack;
import javax.annotation.PreDestroy;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Apache SSH Webserver Connector Implementation
 *
 * Concrete implementation of webserver connector to setup apache webserver via SSH connection.
 *
 * @author Daniel Kröger <daniel.kroeger@kofrezo.io>
 * @author Jan Nurmse
 */
@Named
@RequestScoped
public class ApacheSSHConnectorService implements WebserverConnectorService, Serializable {

    private static final Logger LOGGER = LogManager.getLogger(ApacheSSHConnectorService.class);
    private static final long serialVersionUID = -1150221495148210253L;

    private final int bufferSize = 1024;
    private final String ENVIRMONMENT = System.getProperty("cmf.environment", "development");
    private Properties properties;
    private Session session;

    /**
     * Closing ssh connection.
     */
    @PreDestroy
    public void deinit() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            LOGGER.debug("closing ssh connection to host");
        }
    }

    private Properties getProperties() {
        if (properties == null) {
            try {
                properties = new Properties();
                String propertiesPath = ENVIRMONMENT + "/webserverconnector.properties";
                InputStream is = getClass().getClassLoader().getResourceAsStream(propertiesPath);
                properties.load(is);
            } catch (IOException ex) {
                String error = "/(WEB-INF|META-INF)/classes/webserverconnector.properties does "
                        + "not exist or is not properly configured";
                LOGGER.error(error, ex);
            }
        }
        return properties;
    }

    /**
     * This method provides the ability to override the whole connector properties.
     *
     * @param properties the properties to be set
     */
    public void setProperties(final Properties properties) {
        this.properties = properties;
    }

    private Session getSession() throws JSchException {
        if (session == null || !session.isConnected()) {
            JSch jsch = new JSch();
            jsch.addIdentity(
                    getProperties().getProperty("connector.apachessh.privatekey"),
                    getProperties().getProperty("connector.apachessh.publickey"),
                    getProperties().getProperty("connector.apachessh.passphrase")
                            .getBytes(StandardCharsets.UTF_8)
            );
            session = jsch.getSession(
                   getProperties().getProperty("connector.apachessh.user"),
                   getProperties().getProperty("connector.apachessh.host"),
                    Integer.parseInt(getProperties().getProperty("connector.apachessh.port"))
            );
            session.setConfig(
                    "StrictHostKeyChecking",
                    getProperties().getProperty("connector.apachessh.stricthostkeychecking", "yes")
            );
            session.connect();
            LOGGER.debug("opening ssh session to host");
        }
        return session;
    }

    private String execute(final String command) {
        long start = System.currentTimeMillis();

        try {
            ChannelExec channel = (ChannelExec) getSession().openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(System.err);
            StringBuilder stdout;
            try (InputStream is = channel.getInputStream()) {
                channel.connect();
                stdout = new StringBuilder();
                readStdout(channel, is, stdout);
            }
            channel.disconnect();

            LOGGER.debug("took " + (System.currentTimeMillis() - start)
                    + " ms to execute command via ssh: " + command);

            return stdout.toString();
        } catch (IOException | InterruptedException | JSchException ex) {
            LOGGER.error("executing command via ssh failed", ex);
        }
        return "";
    }

    private void readStdout(final ChannelExec channel, final InputStream is, final StringBuilder stdout)
            throws IOException, InterruptedException {
        byte[] data = new byte[bufferSize];
        while (true) {
            while (is.available() > 0) {
                int read = is.read(data, 0, bufferSize);
                if (read < 0) {
                    break;
                }
                stdout.append(new String(data, 0, read, StandardCharsets.UTF_8));
            }
            if (channel.isClosed()) {
                try {
                    if (is.available() > 0) {
                        continue;
                    }
                    break;
                } catch (IOException ex) {
                    LOGGER.error("executing command via ssh failed", ex);
                }
            }
            Thread.sleep(500); // I absolutely don't know if this is a good value
        }
    }

    private String getTemplate() {
        StringBuilder result = new StringBuilder();
        BufferedInputStream bis = null;
        try {
            String template = getProperties().getProperty("connector.apachessh.template");
            if (template != null) {
                bis = new BufferedInputStream(
                        getClass().getClassLoader().getResourceAsStream(ENVIRMONMENT + "/" + template));
            }
            readResult(bis, result);
        } catch (IOException ex) {
            LOGGER.error("loading template for new domain failed", ex);
        }
        return result.toString();
    }

    private void readResult(final BufferedInputStream bis, final StringBuilder result) throws IOException {
        if (bis != null) {
            byte[] buffer = new byte[bufferSize];
            while (true) {
                int read = bis.read(buffer);
                if (read > -1) {
                    result.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                } else {
                    break;
                }
            }
            try {
                bis.close();
            } catch (IOException ex) {
                LOGGER.error("loading template for new domain failed", ex);
            }
        }
    }

    private String getDocumentRoot(final String template) {
        String docroot = null;

        try {
            int docrootBegin = template.indexOf("DocumentRoot") + 12;
            int docrootEnd = template.indexOf('\n', docrootBegin);

            docroot = template.substring(docrootBegin, docrootEnd).trim();
            LOGGER.debug("document root is: " + docroot);
        } catch (StringIndexOutOfBoundsException ex) {
            LOGGER.warn("failed to get document root from template");
        }

        return docroot;
    }

    private String getFilenameForLs(final String ls) {
        String[] fields = ls.split(" ");
        return fields[fields.length - 1].trim();
    }

    @Override
    public String[] getDomains(final String filter) {
        String command;
        StringBuilder stdout = new StringBuilder();

        switch (filter) {
            case WebserverConnectorService.DOMAIN_FILTER_ENABLED:
                String sitesenabled = "connector.apachessh.sitesenabled";
                command = "grep -P '^\\s+ServerName' "
                        + getProperties().getProperty(sitesenabled, "/etc/apache2/sites-enabled/") + "*";
                stdout.append(execute(command));
                break;
            case WebserverConnectorService.DOMAIN_FILTER_DISABLED:
                throw new UnsupportedOperationException("Not supported yet.");
            default:
                String sitesavailable = "connector.apachessh.sitesavailable";
                command = "grep -P '^\\s+ServerName' "
                        + getProperties().getProperty(sitesavailable, "/etc/apache2/sites-available/") + "*";
                stdout.append(execute(command));
                break;
        }
        List<String> domains = readDomainsFromStdout(stdout);
        String[] tmp = new String[domains.size()];
        return (String[]) domains.toArray(tmp);
    }

    private List<String> readDomainsFromStdout(final StringBuilder stdout) {
        List<String> domains = new ArrayList<>();
        for (String line : stdout.toString().split("\n")) {
            if (!line.trim().equals("")) {
                String[] tmp = line.split("ServerName");
                if (tmp.length > 1) {
                    String domain = tmp[1].trim();
                    domains.add(domain);
                    LOGGER.debug("found domain " + domain);
                }
            }
        }
        LOGGER.debug("found " + domains.size() + " domains");
        return domains;
    }

    @Override
    public void createDomain(final String domain) {
        if (domain != null) {
            String template = getTemplate().replace("%DOMAIN%", domain);
            Stack<String> commands = new Stack();
            String filename = getProperties().getProperty("connector.apachessh.sitesavailable",
                    "/etc/apache2/sites-available/") + domain + ".conf";
            String command1 = "echo '" + template + "' >> " + filename;
            commands.push(command1);
            addAdditionalCommands(commands, template);
            while (!commands.empty()) {
                execute(commands.firstElement());
                commands.remove(0);
            }
        }
    }

    private void addAdditionalCommands(final Stack<String> commands, final String template) {
        String docroot = getDocumentRoot(template);
        if (!docroot.endsWith("/")) {
            docroot += "/";
        }
        if (!docroot.equals("")) {
            String command4 = "mkdir -p " + docroot + "{";
            command4 += WebserverConnectorService.RESOURCE_TYPE_JAVASCRIPT + ",";
            command4 += WebserverConnectorService.RESOURCE_TYPE_OTHER + ",";
            command4 += WebserverConnectorService.RESOURCE_TYPE_STYLESHEET;
            command4 += "}";
            commands.push(command4);
            // all images will be stored in a global directory and not in a domain specific one
            // so that it is necessary to create the global directory and a symlink
            String command5 = "mkdir -p " + RESOURCE_IMG_FOLDER;
            commands.push(command5);
            String command6 = "ln -s " + RESOURCE_IMG_FOLDER + " "
                    + docroot + WebserverConnectorService.RESOURCE_TYPE_IMAGE;
            commands.push(command6);
        }
    }

    @Override
    public void deleteDomain(final String domain) {
        String sitesavailable = "connector.apachessh.sitesavailable";
        String filename = getProperties().getProperty(sitesavailable, "/etc/apache2/sites-available/")
                + domain + ".conf";
        String command = "sudo a2dissite " + domain + ".conf; sudo /etc/init.d/apache2 reload; rm "
                + filename;
        execute(command);
    }

    @Override
    public void enableDomain(final String domain) {
        String command = "sudo a2ensite " + domain + ".conf && sudo /etc/init.d/apache2 reload";
        execute(command);
    }

    @Override
    public void disableDomain(final String domain) {
        String command = "sudo a2dissite " + domain + ".conf && sudo /etc/init.d/apache2 reload";
        execute(command);
    }

    @Override
    public String[] getResources(final String domain, final String type) {
        try {
            String[] types = addTypes(type);
            String[] domains = addDomains(domain);
            Stack<String> resources = new Stack();
            ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
            channel.connect();
            Stack<String> domainPaths = buildDomainPaths(channel);

            for (String curDomain : domains) {
                if (domainShouldBeProcessed(curDomain, domainPaths)) {
                    for (String curType : types) {
                        String path = "/var/www/" + curDomain + "/" + curType;
                        LOGGER.debug("listing resources for " + path);
                        addResourcesForDomainPath(channel, path, resources);
                    }
                }
            }

            channel.disconnect();

            String[] tmp = new String[resources.size()];
            return (String[]) resources.toArray(tmp);
        } catch (JSchException | SftpException ex) {
            LOGGER.error("error while listing available resources", ex);
        }

        return new String[]{};
    }

    private Stack<String> buildDomainPaths(final ChannelSftp channel) throws SftpException {
        Stack<String> domainPaths = new Stack();
        String rootPath = "/var/www";
        for (Object file : channel.ls(rootPath)) {
            String filename = getFilenameForLs(file.toString());
            domainPaths.push(filename);
        }
        return domainPaths;
    }

    private boolean domainShouldBeProcessed(final String domain, final Stack<String> domainPaths) {
        for (String domainPath : domainPaths) {
            if (domainPath.equals(domain)) {
                return true;
            }
        }
        return false;
    }

    private void addResourcesForDomainPath(final ChannelSftp channel, final String path,
            final Stack<String> resources) throws SftpException {
        for (Object file : channel.ls(path)) {
            String filename = getFilenameForLs(file.toString());
            if (!filename.equals(".") && !filename.equals("..")) {
                resources.push(path + "/" + filename);
            }
        }
    }

    private String[] addTypes(final String type) {
        String[] types;
        if (type != null) {
            types = new String[]{type};
        } else {
            types = new String[]{
                WebserverConnectorService.RESOURCE_TYPE_IMAGE,
                WebserverConnectorService.RESOURCE_TYPE_JAVASCRIPT,
                WebserverConnectorService.RESOURCE_TYPE_OTHER,
                WebserverConnectorService.RESOURCE_TYPE_STYLESHEET
            };
        }
        return types;
    }

    private String[] addDomains(final String domain) {
        String[] domains = getDomains(WebserverConnectorService.DOMAIN_FILTER_ALL);
        if (domain != null) {
            domains = new String[]{domain};
        }
        return domains;
    }

    @Override
    public List<String> getResources(final String domain) {
        List<String> fileNames = new ArrayList<>();
        try {
            if (domain != null && !domain.isEmpty()) {

                ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
                channel.connect();

                String path = "/var/www/" + domain;
                fileNames = readFilenames(channel, path, domain);

                channel.disconnect();
            }
        } catch (JSchException ex) {
            LOGGER.error("ApacheSSHConnectorService.getResources", ex);
        }

        return fileNames;
    }

    private List<String> readFilenames(final ChannelSftp channel, final String path, final String domain) {
        List<String> filenames = new ArrayList<>();
        try {
            for (Object file : channel.ls(path)) {
                String filename = getFilenameForLs(file.toString());
                if (!filename.equals(".") && !filename.equals("..")
                        && !filename.equals("wp") && !filename.equals("img")) {
                    File f = new File(path + "/" + filename);
                    if (f.isDirectory()) {
                        filenames.addAll(readFilenames(channel, path + "/" + filename, domain));

                    } else if (f.isFile()) {
                        String qualifiedFilename = path + "/" + filename;
                        qualifiedFilename = qualifiedFilename.replaceAll("/var/www/" + domain + "/", "");
                        filenames.add(qualifiedFilename);
                    }
                }
            }
        } catch (SftpException ex) {
            LOGGER.error("ApacheSSHConnectorService.readFilenames", ex);
        }
        return filenames;
    }

    @Override
    public void createResource(final String domain, final String type, final String src, final String dstName)
            throws JSchException, SftpException {

        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

        String path = "/var/www/" + domain + "/" + type + "/" + dstName;
        channel.put(src, path);
        channel.disconnect();
    }

    @Override
    public void createImage(final InputStream src, final String dstName) throws JSchException, SftpException {
        String folder = RESOURCE_IMG_FOLDER + "/" + dstName.substring(0, dstName.lastIndexOf('/'));
        String command = "mkdir -p " + folder;
        execute(command);
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

        String path = RESOURCE_IMG_FOLDER + "/" + dstName;
        channel.put(src, path);
        channel.disconnect();
    }

    @Override
    public void createResource(final String domain, final String type, final InputStream src,
            final String dstName) throws JSchException, SftpException {

        String folder = "/var/www/" + domain + "/" + type + "/";
        String command = "mkdir -p " + folder;
        execute(command);
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

        String path = folder + "/" + dstName;
        channel.put(src, path);
        channel.disconnect();

    }

    @Override
    public void createWebserverResource(final String domain, final InputStream src, final String uploadPath,
            final String dstName) throws JSchException, SftpException {
        createDirectoryForWebserverResource(domain, uploadPath);
        String folder = "/var/www/" + domain + "/";
        if (uploadPath != null && !uploadPath.isEmpty()) {
            folder = folder + uploadPath;
        }

        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();
        String path = folder + "/" + dstName;
        channel.put(src, path);
        channel.disconnect();

    }

    private void createDirectoryForWebserverResource(final String domain, final String uploadPath)
            throws JSchException {
        String folder = "/var/www/" + domain + "/";
        if (uploadPath != null && !uploadPath.isEmpty()) {
            folder = folder + uploadPath + "/";
        }
        String command = "mkdir -p " + folder;
        execute(command);
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

    }

    @Override
    public void deleteResource(final String domain, final String type, final String name)
            throws JSchException, SftpException {
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

        String path = "/var/www/" + domain + "/" + type + "/" + name;
        channel.rm(path);
        channel.disconnect();
    }

    @Override
    public void deleteWebserverResource(final String domain, final String name)
            throws JSchException, SftpException {
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();

        String path = "/var/www/" + domain + "/" + name;
        channel.rm(path);
        channel.disconnect();
    }

    @Override
    public void copyResources(final String sourceDomain, final String destinationDomain) {
        String sourcePath = "/var/www/" + sourceDomain + "/";
        String destinationPath = "/var/www/" + destinationDomain + "/";
        String command = "cp -R " + sourcePath + "* " + destinationPath;
        execute(command);
    }

    @Override
    public void copySingleResource(final String sourceDomain, final String destinationDomain,
            final String type, final String resourceName) {
        String sourcePath = "/var/www/" + sourceDomain + "/" + resourceName;
        String destinationPath = "/var/www/" + destinationDomain + "/" + resourceName;
        if (type != null && type.isEmpty()) {
            sourcePath = "/var/www/" + sourceDomain + "/" + type + "/" + resourceName;
            destinationPath = "/var/www/" + destinationDomain + "/" + type + "/" + resourceName;
        }

        String command = "cp -R " + sourcePath + " " + destinationPath;
        execute(command);
    }

    @Override
    public InputStream readWebserverResource(final String domain, final String name)
            throws JSchException, SftpException {
        ChannelSftp channel = (ChannelSftp) getSession().openChannel("sftp");
        channel.connect();
        String path = "/var/www/" + domain + "/" + name;
        return channel.get(path);
    }
}
