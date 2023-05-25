package edu.umassmed.OmeroImporter;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

import edu.umassmed.OmeroDataWriter.OmeroDataWriter;
import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataLevel;
import ome.formats.OMEROMetadataStoreClient;
import ome.formats.importer.ImportCandidates;
import ome.formats.importer.ImportConfig;
import ome.formats.importer.ImportLibrary;
import ome.formats.importer.OMEROWrapper;
import ome.formats.importer.cli.ErrorHandler;
import ome.formats.importer.cli.LoggingImportMonitor;
import omero.ServerError;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.AdminFacility;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.DataManagerFacility;
import omero.gateway.model.DatasetData;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.ProjectData;
import omero.log.Logger;
import omero.log.SimpleLogger;
import omero.model.Dataset;
import omero.model.DatasetI;
import omero.model.Project;
import omero.model.ProjectDatasetLink;
import omero.model.ProjectDatasetLinkI;
import omero.model.ProjectI;

// TODO eliminate import / metadata written if previously imported
// add previously imported instead
// present better error if copy files failed cuz its already there

public class OmeroImporter {
	private final ImportConfig config;
	private OMEROMetadataStoreClient store;
	private OMEROWrapper reader;
	private ErrorHandler handler;
	private ImportLibrary library;
	private BrowseFacility browser;
	private AdminFacility admin;
	private DataManagerFacility dataManager;
	private SecurityContext ctx;
	private Gateway gateway;
	private LoginCredentials cred;

	private final String extFilter = "";
	private final String nameFilter = "";

	private OmeroDataWriter dataWriter;
	
	private static String outputLogFileName = "OmeroImporter_log.txt";
	private static String outputMetadataLogFileName = "OmeroImporter_metadata_log.txt";
	private static String outputImportedFileName = "OmeroImporter_imported.txt";
	private static String outputMetadataFileName = "OmeroImporter_metadata.txt";
	private static String configFileFolder = "OmeroImporter";
	private static String configFileName = "OmeroImporter.cfg";

	private static String SCRIPTS = "scripts";
	private static String B2_LOCAL_STRING = "b2-tools" + File.separator
			+ "b2-windows" + File.separator + "1";
	private static String B2_COMMAND = "b2-windows-1.exe";
	
	private static String EMAIL_FROM_ADDRESS = "youradmin@email.com";
	private static String EMAIL_APP_PASSWORD = "youradminpassword";

	private final File configFile;
	private File outputLogFile;
	private File outputMetadataLogFile;
	private File outputImportedFile;
	private File outputMetadataFile;

	private boolean metadataLogFileNeeded;
	private boolean headless;
	private boolean deleteFiles;
	private boolean hasMMAFiles;
	private boolean hasB2;

	private String target;
	private String destination;
	private String b2AppKeyId;
	private String b2AppKey;
	private String b2BucketName;
	private final String b2Command;
	
	private String emailTo;
	private String adminEmailTo;
	
	private JFrame hiddenFrame;

	private static boolean IGNORE_TIME = true;

	private static String metadataIdent = "Pazour Lab Experimental Metadata - \\S+";
	final DateTimeFormatter formatter = DateTimeFormatter
			.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private int timeStart;
	private int timeEnd;
	private final static String CSV_EXT = ".csv";
	private final static String JSON_EXT = ".json";
	
	public OmeroImporter() {
		
		this.headless = false;
		this.deleteFiles = false;
		this.hasMMAFiles = false;
		this.hasB2 = false;

		this.target = null;
		this.destination = null;
		this.b2AppKeyId = null;
		this.b2AppKey = null;
		this.b2BucketName = null;
		
		this.emailTo = null;
		this.adminEmailTo = null;

		this.timeStart = -1;
		this.timeEnd = -1;

		String b2Command = System.getProperty("user.dir") + File.separator
				+ OmeroImporter.SCRIPTS + File.separator
				+ OmeroImporter.B2_COMMAND;
		if (!new File(b2Command).exists()) {
			b2Command = System.getProperty("user.dir") + File.separator
					+ OmeroImporter.SCRIPTS + File.separator
					+ OmeroImporter.B2_LOCAL_STRING + File.separator
					+ OmeroImporter.B2_COMMAND;
		}
		System.out.println(b2Command);
		this.b2Command = b2Command;

		final String homeFolder = System.getProperty("user.home");
		final String configFileString = homeFolder + File.separator
				+ OmeroImporter.configFileFolder + File.separator
				+ OmeroImporter.configFileName;

		this.configFile = new File(configFileString);
		
		this.config = new ome.formats.importer.ImportConfig();
		
		this.config.email.set("");
		this.config.sendFiles.set(true);
		this.config.sendReport.set(false);
		this.config.contOnError.set(false);
		this.config.debug.set(false);

		this.metadataLogFileNeeded = false;
	}
	
	public void setServerAccessInformation(final String hostName_arg,
			final Integer port_arg, final String userName_arg,
			final String psw_arg) {
		
		final String hostName = hostName_arg;
		final Integer port = port_arg;
		final String userName = userName_arg;
		final String psw = psw_arg;
		
		this.config.hostname.set(hostName);
		this.config.port.set(port);
		this.config.username.set(userName);
		this.config.password.set(psw);
		
		this.cred = new LoginCredentials(userName, psw, hostName, port);
		this.dataWriter = new OmeroDataWriter(hostName, port, userName, psw);
	}

	public void init() throws Exception {
		this.store = this.config.createStore();
		this.store.logVersionInfo(this.config.getIniVersionNumber());
		this.reader = new OMEROWrapper(this.config);
		this.library = new ImportLibrary(this.store, this.reader);
		this.handler = new ErrorHandler(this.config);
		this.library.addObserver(new LoggingImportMonitor());

		final Logger simpleLogger = new SimpleLogger();
		this.gateway = new Gateway(simpleLogger);
		this.browser = this.gateway.getFacility(BrowseFacility.class);
		this.admin = this.gateway.getFacility(AdminFacility.class);
		this.dataManager = this.gateway.getFacility(DataManagerFacility.class);
		final ExperimenterData user = this.gateway.connect(this.cred);
		this.ctx = new SecurityContext(user.getGroupId());

		this.dataWriter.init();
		
		this.hiddenFrame = new JFrame("Hidden frame");
		// this.hiddenFrame.setVisible(true);
		this.hiddenFrame.setSize(1, 1);
		this.hiddenFrame
				.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		final Dimension screenSize = Toolkit.getDefaultToolkit()
				.getScreenSize();
		final int posX = (int) (screenSize.getWidth() / 2);
		final int posY = (int) (screenSize.getHeight() / 2);
		this.hiddenFrame.setLocation(posX, posY);
	}

	public Map<String, String> readConfigFile() {
		final Map<String, String> parameters = new LinkedHashMap<String, String>();
		final File f = this.configFile;

		FileReader fr;
		BufferedReader br;
		try {
			fr = new FileReader(f);
			br = new BufferedReader(fr);
		} catch (final FileNotFoundException ex) {
			this.writeToLog("Opening file error in OmeroImporter for "
					+ OmeroImporter.configFileName + "\n");
			this.writeToLog(ex.getMessage() + "\n");
			return null;
		}

		try {
			String line = br.readLine();
			while (line != null) {
				if (line.startsWith("//") || line.startsWith("#")) {
					line = br.readLine();
					continue;
				}
				final String[] tokens = line.split("\t");
				parameters.put(tokens[0], tokens[1]);
				line = br.readLine();
			}
		} catch (final IOException ex) {
			this.writeToLog("Reading line error in OmeroImporter for "
					+ OmeroImporter.configFileName + "\n");
			this.writeToLog(ex.getMessage() + "\n");
			return null;
		}
		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for "
					+ OmeroImporter.configFileName + "\n");
			this.writeToLog(ex.getMessage() + "\n");
			return null;
		}
		return parameters;
	}

	public Long retrieveImageId(final String imageName,
			final String datasetName, final String projectName)
			throws DSOutOfServiceException, DSAccessException {
		List<Long> datasetIDs = this.retrieveDatasetIDsFromProject(projectName);
		final Collection<DatasetData> datasets = this.browser
				.getDatasets(this.ctx, datasetIDs);

		final Iterator<DatasetData> i = datasets.iterator();
		DatasetData dataset = null;
		while (i.hasNext()) {
			dataset = i.next();
			if (dataset.getName().equals(datasetName)) {
				break;
			}
		}
		if (dataset == null)
			return -1L;

		datasetIDs = new ArrayList<Long>();
		datasetIDs.add(dataset.getId());
		final Collection<ImageData> images = this.browser
				.getImagesForDatasets(this.ctx, datasetIDs);

		final Iterator<ImageData> i2 = images.iterator();
		ImageData image;
		while (i2.hasNext()) {
			image = i2.next();
			if (image.getName().equals(imageName))
				return image.getId();
		}

		return -1L;
	}

	public Long retrieveDatasetId(final String datasetName,
			final String projectName)
			throws DSOutOfServiceException, DSAccessException {
		final List<Long> datasetIDs = this
				.retrieveDatasetIDsFromProject(projectName);
		final Collection<DatasetData> datasets = this.browser
				.getDatasets(this.ctx, datasetIDs);
		
		final Iterator<DatasetData> i = datasets.iterator();
		DatasetData dataset;
		while (i.hasNext()) {
			dataset = i.next();
			if (dataset.getName().equals(datasetName))
				return dataset.getId();
		}
		return -1L;
	}
	
	public List<Long> retrieveDatasetIDsFromProject(final String projectName)
			throws DSOutOfServiceException, DSAccessException {
		final List<Long> datasetIDs = new ArrayList<Long>();
		final Collection<ProjectData> projects = this.browser
				.getProjects(this.ctx);
		final Iterator<ProjectData> i = projects.iterator();
		ProjectData project;
		while (i.hasNext()) {
			project = i.next();
			if (project.getName().equals(projectName)) {
				for (final DatasetData dataset : project.getDatasets()) {
					datasetIDs.add(dataset.getId());
				}
			}
		}
		
		return datasetIDs;
	}

	public Long retrieveProjectId(final String projectName)
			throws DSOutOfServiceException, DSAccessException {
		final Collection<ProjectData> projects = this.browser
				.getProjects(this.ctx);
		
		final Iterator<ProjectData> i = projects.iterator();
		ProjectData project;
		while (i.hasNext()) {
			project = i.next();
			if (project.getName().equals(projectName))
				return project.getId();
		}
		return -1L;
	}
	
	public Long retrieveUserId(final String userName)
			throws DSOutOfServiceException, DSAccessException {
		final ExperimenterData experimenter = this.admin
				.lookupExperimenter(this.ctx, userName);
		if (experimenter == null)
			return -1L;
		return experimenter.getId();
	}

	public void close() {
		if (!this.metadataLogFileNeeded) {
			this.outputMetadataLogFile.delete();
		}
		if (this.hiddenFrame != null) {
			this.hiddenFrame.dispose();
		}
		if (this.dataWriter != null) {
			this.dataWriter.close();
		}
		if (this.gateway != null) {
			this.gateway.disconnect();
		}
		if (this.store != null) {
			this.store.logout();
		}
	}
	
	private void initLogFiles() {
		final LocalDateTime now = LocalDateTime.now();
		final String date = now.format(this.formatter);
		
		final String path = System.getProperty("user.dir") + File.separator;
		
		final String outputLogFileName = path + date + "_"
				+ OmeroImporter.outputLogFileName;
		this.outputLogFile = new File(outputLogFileName);
		try {
			this.outputLogFile.createNewFile();
		} catch (final IOException ex) {
			this.printToConsole("OmeroImporter - creating log file failed");
			this.printToConsole(ex.getMessage());
		}

		final String outputMetadataLogFileName = path + date + "_"
				+ OmeroImporter.outputMetadataLogFileName;
		this.outputMetadataLogFile = new File(outputMetadataLogFileName);
		try {
			this.outputMetadataLogFile.createNewFile();
		} catch (final IOException ex) {
			this.writeToLog("Creating file error in OmeroImporter for "
					+ OmeroImporter.outputMetadataLogFileName + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		
		final String outputImportedFileName = path + date + "_"
				+ OmeroImporter.outputImportedFileName;
		this.outputImportedFile = new File(outputImportedFileName);
		try {
			this.outputImportedFile.createNewFile();
		} catch (final IOException ex) {
			this.writeToLog("Creating file error in OmeroImporter for "
					+ OmeroImporter.outputImportedFileName + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}

		final String outputMetadataFileName = path + date + "_"
				+ OmeroImporter.outputMetadataFileName;
		this.outputMetadataFile = new File(outputMetadataFileName);
		try {
			this.outputMetadataFile.createNewFile();
		} catch (final IOException ex) {
			this.writeToLog("Creating file error in OmeroImporter for "
					+ OmeroImporter.outputMetadataFileName + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		this.writeToLog("OmeroImporter started\n");
	}

	private void writeToMetadataLogAndLog(final String s) {
		final LocalDateTime now = LocalDateTime.now();
		final String nowFormat = now.format(this.formatter);
		
		this.writeToLog(s);

		final String filename = this.outputMetadataLogFile.getName();
		this.metadataLogFileNeeded = true;
		FileWriter fw = null;
		try {
			fw = new FileWriter(this.outputMetadataLogFile, true);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for "
					+ filename + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		final BufferedWriter bw = new BufferedWriter(fw);
		
		try {
			if (s == null) {
				bw.write(nowFormat + " - " + "No Message");
			} else {
				bw.write(nowFormat + " - " + s);
			}
		} catch (final IOException ex) {
			this.writeToLog(
					"Write line error in OmeroImporter for " + filename + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		
		try {
			bw.close();
			fw.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for "
					+ filename + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
	}

	private void sendErrorEmail(final String error) {
		final String subject = "Omero Import error report";
		String text = "Omero Importer job has been terminated due to the following error:\n";
		text += error + "\n\n";
		this.sendEmail(subject, text);
		this.sendAdminEmail(subject, text);
	}

	// private void sendCompleteNoImportEmail(final List<String> errors) {
	// final String subject = "Omero Importer job completion report";
	// String text = "Omero Importer job successfully complete.\nNo images have
	// been imported.\n";
	// if (errors.size() > 0) {
	// text += "This could have been caused by the following problems:\n";
	// for (final String s : errors) {
	// text += s + "\n";
	// }
	// } else {
	// text += "No clear problems have been indentified.\n";
	// }
	// text += "Please consult the log files for more information.\n";
	// text += "\n";
	// this.sendEmail(subject, text);
	// this.sendAdminEmail(subject, text);
	//
	// }
	
	private void sendCompleteEmail(final List<String> imported,
			final List<String> metadataWritten, final List<String> errors) {
		final String subject = "Omero Importer job completion report";
		String text = "Omero Importer job successfully complete.\n";
		if (imported.size() > 0) {
			text += "The following file have been imported:\n";
			for (final String s : imported) {
				text += s + "\n";
			}
		} else {
			text += "No file was imported.\n";
		}
		if (metadataWritten.size() > 0) {
			text += "The following metadata have been written:\n";
			for (final String s : metadataWritten) {
				text += s + "\n";
			}
		} else {
			text += "No metadata was written.\n";
		}
		if (errors.size() > 0) {
			text += "The following problems have been encountered:\n";
			for (final String s : errors) {
				text += s + "\n";
			}
		} else {
			text += "No problems was encountered.\n";
		}
		text += "\n";
		this.sendEmail(subject, text);
		this.sendAdminEmail(subject, text);
	}
	
	private void sendAdminEmail(final String subject, final String s) {
		String body = s;
		body += "\nThis is an automatic message from an unsupervised email address, please do not reply to this mail.\n";
		body += "If you need assistance contact caterina.strambio@umassmed.edu";
		final Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.port", "465");
		properties.setProperty("mail.transport.protocol", "smtp");
		properties.setProperty("mail.smtp.auth", "true");
		properties.setProperty("mail.smtp.starttls.enable", "true");
		properties.setProperty("mail.smtp.starttls.required", "true");
		properties.setProperty("mail.debug", "true");
		properties.setProperty("mail.smtp.ssl.enable", "true");
		final Session session = Session.getDefaultInstance(properties);
		try {
			final MimeMessage message = new MimeMessage(session);
			// message.setFrom(new InternetAddress(OmeroImporter.EMAIL_CC));
			message.addRecipient(RecipientType.TO,
					new InternetAddress(this.adminEmailTo));
			message.setSubject(subject);
			// message.setText(body);

			final MimeBodyPart mimeBodyPart = new MimeBodyPart();
			mimeBodyPart.setContent(body, "text/plain; charset=utf-8");

			final Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(mimeBodyPart);

			final MimeBodyPart attachmentBodyPart1 = new MimeBodyPart();
			attachmentBodyPart1.attachFile(this.outputLogFile);
			multipart.addBodyPart(attachmentBodyPart1);

			final MimeBodyPart attachmentBodyPart2 = new MimeBodyPart();
			attachmentBodyPart2.attachFile(this.outputMetadataLogFile);
			multipart.addBodyPart(attachmentBodyPart2);

			final MimeBodyPart attachmentBodyPart3 = new MimeBodyPart();
			attachmentBodyPart3.attachFile(this.outputImportedFile);
			multipart.addBodyPart(attachmentBodyPart3);

			final MimeBodyPart attachmentBodyPart4 = new MimeBodyPart();
			attachmentBodyPart4.attachFile(this.outputMetadataFile);
			multipart.addBodyPart(attachmentBodyPart4);
			
			message.setContent(multipart);

			final Transport tr = session.getTransport("smtp");
			tr.connect("smtp.gmail.com", OmeroImporter.EMAIL_FROM_ADDRESS,
					OmeroImporter.EMAIL_APP_PASSWORD);
			message.saveChanges();
			tr.sendMessage(message, message.getAllRecipients());
			tr.close();
		} catch (final MessagingException ex) {
			this.writeToLog("Error sending mail" + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		} catch (final IOException ex) {
			this.writeToLog("Error sending mail" + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}

	}

	private void sendEmail(final String subject, final String s) {
		String body = s;
		body += "\nThis is an automatic message from an unsupervised email address, please do not reply to this mail.\n";
		body += "If you need assistance contact caterina.strambio@umassmed.edu";
		final Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.port", "465");
		properties.setProperty("mail.transport.protocol", "smtp");
		properties.setProperty("mail.smtp.auth", "true");
		properties.setProperty("mail.smtp.starttls.enable", "true");
		properties.setProperty("mail.smtp.starttls.required", "true");
		properties.setProperty("mail.debug", "true");
		properties.setProperty("mail.smtp.ssl.enable", "true");
		final Session session = Session.getDefaultInstance(properties);
		try {
			final MimeMessage message = new MimeMessage(session);
			// message.setFrom(new InternetAddress(OmeroImporter.EMAIL_CC));
			message.addRecipient(RecipientType.TO,
					new InternetAddress(this.emailTo));
			message.setSubject(subject);
			message.setText(body);
			final Transport tr = session.getTransport("smtp");
			tr.connect("smtp.gmail.com", OmeroImporter.EMAIL_FROM_ADDRESS,
					OmeroImporter.EMAIL_APP_PASSWORD);
			message.saveChanges();
			tr.sendMessage(message, message.getAllRecipients());
			tr.close();
		} catch (final MessagingException ex) {
			this.writeToLog("Error sending mail" + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}

	}

	private void printToConsole(final String s) {
		final LocalDateTime now = LocalDateTime.now();
		final String nowFormat = now.format(this.formatter);
		System.out.println(nowFormat + " - " + s);

	}
	
	private void writeToLog(final String s) {
		final LocalDateTime now = LocalDateTime.now();
		final String nowFormat = now.format(this.formatter);
		FileWriter fw = null;
		try {
			fw = new FileWriter(this.outputLogFile, true);
		} catch (final IOException ex) {
			this.printToConsole("OmeroImporter - opening log file failed");
			this.printToConsole(ex.getMessage());
		}
		final BufferedWriter bw = new BufferedWriter(fw);
		
		try {
			if (s == null) {
				bw.write(nowFormat + " - " + "No Message");
			} else {
				bw.write(nowFormat + " - " + s);
			}
		} catch (final IOException ex) {
			this.printToConsole("OmeroImporter - writing log file failed");
			this.printToConsole(ex.getMessage());
		}
		
		try {
			bw.close();
			fw.close();
		} catch (final IOException ex) {
			this.printToConsole("OmeroImporter - closing log file failed");
			this.printToConsole(ex.getMessage());
		}

	}
	
	private void writeToPreviousMetadataFile(final List<String> imported) {
		FileWriter fw = null;
		final String filename = this.outputMetadataFile.getName();
		try {
			fw = new FileWriter(this.outputMetadataFile, true);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for "
					+ filename + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		final BufferedWriter bw = new BufferedWriter(fw);

		try {
			bw.write("Files Metadata was written for:\n");
			for (final String s : imported) {
				bw.write("File,");
				bw.write(s);
				bw.write("\n");
			}
		} catch (final IOException ex) {
			this.writeToLog(
					"Write line error in OmeroImporter for " + filename + "\n");
			this.writeToLog(ex.getMessage());
		}

		try {
			bw.close();
			fw.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for "
					+ filename + "\n");
			this.writeToLog(ex.getMessage());
		}
	}
	
	private void writeToPreviousImportedFile(final List<String> imported) {
		FileWriter fw = null;
		final String filename = this.outputImportedFile.getName();
		try {
			fw = new FileWriter(this.outputImportedFile, true);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for "
					+ filename + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		final BufferedWriter bw = new BufferedWriter(fw);

		try {
			bw.write("Files imported:\n");
			for (final String s : imported) {
				bw.write("File,");
				bw.write(s);
				bw.write("\n");
			}
		} catch (final IOException ex) {
			this.writeToLog(
					"Write line error in OmeroImporter for " + filename + "\n");
			this.writeToLog(ex.getMessage());
		}

		try {
			bw.close();
			fw.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for "
					+ filename + "\n");
			this.writeToLog(ex.getMessage());
		}
	}

	private List<String> readFromPreviousMetadataFiles() {
		final String path = System.getProperty("user.dir");
		final List<String> previousMetadata = new ArrayList<String>();
		final File dir = new File(path);
		for (final File f : dir.listFiles()) {
			if (!f.getName().contains(OmeroImporter.outputMetadataFileName)
					|| f.getName().equals(this.outputMetadataFile.getName())) {
				continue;
			}
			final List<String> tmpPreviousMetadata = this
					.readFromPreviousMetadataFile(f);
			previousMetadata.addAll(tmpPreviousMetadata);
		}
		return previousMetadata;
	}

	private List<String> readFromPreviousImportedFiles() {
		final String path = System.getProperty("user.dir");
		final List<String> previousImported = new ArrayList<String>();
		final File dir = new File(path);
		for (final File f : dir.listFiles()) {
			if (!f.getName().contains(OmeroImporter.outputImportedFileName)
					|| f.getName().equals(this.outputImportedFile.getName())) {
				continue;
			}
			final List<String> tmpPreviousImported = this
					.readFromPreviousImportedFile(f);
			previousImported.addAll(tmpPreviousImported);
		}
		return previousImported;
	}
	
	private List<String> readFromPreviousMetadataFile(final File f) {
		// final File f = new File(OmeroImporterFlat.outputImportedFileName);
		final List<String> previousMetadata = new ArrayList<String>();
		if (!f.exists())
			return previousMetadata;
		FileReader fr = null;
		try {
			fr = new FileReader(f);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		final BufferedReader br = new BufferedReader(fr);
		
		String line = null;
		try {
			line = br.readLine();
			while (line != null) {
				final String tokens[] = line.split(",");
				if ((tokens.length < 2)
						|| (!tokens[0].toLowerCase().equals("file"))) {
					line = br.readLine();
					continue;
				}
				final String value = tokens[1];
				previousMetadata.add(value);
				line = br.readLine();
			}
		} catch (final IOException ex) {
			this.writeToLog("Read line error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		
		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		return previousMetadata;
	}

	private List<String> readFromPreviousImportedFile(final File f) {
		// final File f = new File(OmeroImporterFlat.outputImportedFileName);
		final List<String> previousImported = new ArrayList<String>();
		if (!f.exists())
			return previousImported;
		FileReader fr = null;
		try {
			fr = new FileReader(f);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		final BufferedReader br = new BufferedReader(fr);
		
		String line = null;
		try {
			line = br.readLine();
			while (line != null) {
				final String tokens[] = line.split(",");
				if ((tokens.length < 2)
						|| (!tokens[0].toLowerCase().equals("file"))) {
					line = br.readLine();
					continue;
				}
				final String value = tokens[1];
				previousImported.add(value);
				line = br.readLine();
			}
		} catch (final IOException ex) {
			this.writeToLog("Read line error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		
		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		return previousImported;
	}
	
	private Map<String, File> readMMAMetadaFiles(final File rootDir) {
		final Map<String, File> filesMap = new LinkedHashMap<String, File>();
		final List<String> mmaNecessary = new ArrayList<String>();
		// Read csv metadata files first and collect data

		for (final File project : rootDir.listFiles()) {
			if (project.isDirectory()) {
				final String projectName = project.getName();
				final String projectID = projectName.toLowerCase();
				for (final File dataset : project.listFiles()) {
					if (dataset.isDirectory()) {
						final String datasetName = dataset.getName();
						final String datasetID = projectID + File.separator
								+ datasetName.toLowerCase();
						for (final File image : dataset.listFiles()) {
							final String imageName = image.getName();
							if (image.isDirectory()
									|| imageName.endsWith(OmeroImporter.CSV_EXT)
									|| imageName
											.endsWith(OmeroImporter.JSON_EXT)) {
								continue;
							}
							
							final int lastIndex = imageName.lastIndexOf(".");
							String nameNoExt = imageName;
							if (lastIndex != -1) {
								final String ext = imageName
										.substring(lastIndex);
								nameNoExt = imageName.replace(ext, "");
							}
							final String imageID = datasetID + File.separator
									+ nameNoExt.toLowerCase();
							mmaNecessary.add(imageID);
						}
						mmaNecessary
								.add(datasetID + File.separator + "microscope");
					}
				}
			}
		}
		for (final File project : rootDir.listFiles()) {
			if (project.isDirectory()) {
				final String projectName = project.getName();
				final String projectID = projectName.toLowerCase();
				for (final File dataset : project.listFiles()) {
					if (dataset.isDirectory()) {
						final String datasetName = dataset.getName();
						final String datasetID = projectID + File.separator
								+ datasetName.toLowerCase();
						for (final File image : dataset.listFiles()) {
							if (image.isDirectory()) {
								continue;
							}
							final String imageName = image.getName();
							if (!imageName.endsWith(OmeroImporter.JSON_EXT)) {
								continue;
							}
							final String name = imageName
									.replace(OmeroImporter.JSON_EXT, "");
							final int lastIndex = name.lastIndexOf(".");
							String nameNoExt = name;
							if (lastIndex != -1) {
								final String ext = name.substring(lastIndex);
								nameNoExt = name.replace(ext, "");
							}
							String imageID = datasetID + File.separator
									+ nameNoExt;
							String imageFullName = projectName + File.separator
									+ datasetName + File.separator + imageName;
							if (mmaNecessary.contains(imageID)) {
								this.writeToLog(
										"Successfully read MMA metadata for ");
								this.writeToLog(imageFullName + "\n");
								filesMap.put(imageID, image);
								mmaNecessary.remove(imageID);
							} else {
								imageID = datasetID + File.separator
										+ "microscope";
								imageFullName = projectName + File.separator
										+ datasetName + File.separator
										+ "microscope";
								this.writeToLog(
										"Successfully read MMA microscope metadata for ");
								this.writeToLog(imageFullName + "\n");
								filesMap.put(imageID, image);
								mmaNecessary.remove(imageID);
							}
						}
					}
				}
			}
		}
		
		if (!mmaNecessary.isEmpty() && !this.headless) {
			
			String message = "Missing MMA metadata for ";
			message += mmaNecessary.size();
			message += " entitie(s) : \n";
			for (final String s : mmaNecessary) {
				message += s;
				message += "\n";
			}
			message += "Continue the importing?";
			
			this.hiddenFrame.setVisible(true);
			final int response = JOptionPane.showConfirmDialog(this.hiddenFrame,
					message, "Warning metadata missing",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			
			switch (response) {
				case JOptionPane.NO_OPTION:
					return null;
				case JOptionPane.CLOSED_OPTION:
				default:
			}
			this.hiddenFrame.setVisible(false);
		}
		
		return filesMap;
	}
	
	private Map<String, Map<String, String>> readCSVMetadaFiles(
			final File rootDir) {
		final Map<String, Map<String, String>> keyValuesMap = new LinkedHashMap<String, Map<String, String>>();
		final List<String> csvNecessary = new ArrayList<String>();
		// Read csv metadata files first and collect data
		for (final File container : rootDir.listFiles()) {
			if (!container.isDirectory()) {
				continue;
			}
			for (final File project : container.listFiles()) {
				if (project.isDirectory()) {
					final String projectName = project.getName();
					final String projectID = projectName.toLowerCase();
					csvNecessary.add(projectID);
					for (final File dataset : project.listFiles()) {
						if (dataset.isDirectory()) {
							final String datasetName = dataset.getName();
							final String datasetID = projectID + File.separator
									+ datasetName.toLowerCase();
							csvNecessary.add(datasetID);
							for (final File image : dataset.listFiles()) {
								final String imageName = image.getName();
								if (image.isDirectory()
										|| imageName
												.endsWith(OmeroImporter.CSV_EXT)
										|| imageName.endsWith(
												OmeroImporter.JSON_EXT)) {
									continue;
								}

								final int lastIndex = imageName
										.lastIndexOf(".");
								String nameNoExt = imageName;
								if (lastIndex != -1) {
									final String ext = imageName
											.substring(lastIndex);
									nameNoExt = imageName.replace(ext, "");
								}
								final String imageID = datasetID
										+ File.separator
										+ nameNoExt.toLowerCase();
								csvNecessary.add(imageID);
							}
						}
					}
				}
			}
		}
		for (final File container : rootDir.listFiles()) {
			if (!container.isDirectory()) {
				continue;
			}
			for (final File project : container.listFiles()) {
				final String projectName = project.getName();
				String projectID = projectName.toLowerCase();
				if (project.isDirectory()) {
					for (final File dataset : project.listFiles()) {
						final String datasetName = dataset.getName();
						final String datasetFullName = projectName
								+ File.separator + datasetName;
						String datasetID = datasetFullName.toLowerCase();
						if (dataset.isDirectory()) {
							for (final File image : dataset.listFiles()) {
								if (image.isDirectory()) {
									continue;
								}
								final String imageName = image.getName();
								final String imageFullName = datasetFullName
										+ File.separator + imageName;
								// String imageID = imageFullName.toLowerCase();
								if (!imageName
										.endsWith(OmeroImporter.CSV_EXT)) {
									continue;
								}
								
								// MULTIFILE CSV how to handle?
								Map<String, Map<String, String>> imagesMap = null;
								imagesMap = this.readMetadataFromFileMultifile(
										datasetID + File.separator, image, 1);
								if (imagesMap == null) {
									this.writeToMetadataLogAndLog(
											"Error during reading of metadata for "
													+ imageFullName + "\n");
									continue;
								}
								if (imagesMap.isEmpty()) {
									this.writeToMetadataLogAndLog(
											"Read empty metadata for "
													+ imageFullName + "\n");
									continue;
								}
								final Set<String> keys = imagesMap.keySet();
								keyValuesMap.putAll(imagesMap);
								
								for (final String key : keys) {
									this.writeToLog(
											"Successfully read metadata for "
													+ key + "\n");
									csvNecessary.remove(key);
								}
								
							}
							continue;
						}
						if (!datasetName.endsWith(OmeroImporter.CSV_EXT)) {
							continue;
						}
						Map<String, String> datasetMap = null;
						datasetMap = this.readMetadataFromFile(dataset, 1);
						if (datasetMap == null) {
							this.writeToMetadataLogAndLog(
									"Error during reading of metadata for "
											+ datasetFullName + "\n");
							continue;
						}
						if (datasetMap.isEmpty()) {
							this.writeToMetadataLogAndLog(
									"Read empty metadata for " + datasetFullName
											+ "\n");
							continue;
						}
						datasetID = datasetID.replace(OmeroImporter.CSV_EXT,
								"");
						keyValuesMap.put(datasetID, datasetMap);
						csvNecessary.remove(datasetID);
						this.writeToLog("Successfully read metadata for ");
						this.writeToLog(datasetFullName + "\n");
					}
					continue;
				}
				if (!projectName.endsWith(OmeroImporter.CSV_EXT)) {
					continue;
				}
				Map<String, String> projectMap = null;
				projectMap = this.readMetadataFromFile(project, 1);
				if (projectMap == null) {
					this.writeToMetadataLogAndLog(
							"Error during reading of metadata for "
									+ projectName + "\n");
					continue;
				}
				if (projectMap.isEmpty()) {
					this.writeToMetadataLogAndLog(
							"Read empty metadata for " + projectName + "\n");
					continue;
				}
				projectID = projectID.replace(OmeroImporter.CSV_EXT, "");
				keyValuesMap.put(projectID, projectMap);
				csvNecessary.remove(projectID);
				this.writeToLog(
						"Successfully read metadata for " + projectName + "\n");
			}
		}

		if (!csvNecessary.isEmpty() && !this.headless) {

			String message = "Missing metadata for ";
			message += csvNecessary.size();
			message += " entitie(s) : \n";
			for (final String s : csvNecessary) {
				message += s;
				message += "\n";
			}
			message += "Continue the importing?";
			
			this.hiddenFrame.setVisible(true);
			final int response = JOptionPane.showConfirmDialog(this.hiddenFrame,
					message, "Warning metadata missing",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

			switch (response) {
				case JOptionPane.NO_OPTION:
					return null;
				case JOptionPane.CLOSED_OPTION:
				default:
			}
			this.hiddenFrame.setVisible(false);
		}
		
		return keyValuesMap;
	}

	private void importImages() throws IOException, InterruptedException {
		final File rootDir = new File(this.target);

		// if (!rootDir.isDirectory()) {
		// printToConsole(targetPath + " is not a valid directory");
		// return;
		// }

		if (this.hasB2) {
			final String b2AuthorizeCommand = this.b2Command
					+ " authorize-account " + this.b2AppKeyId + " "
					+ this.b2AppKey;
			final Process process = Runtime.getRuntime()
					.exec(b2AuthorizeCommand);
			final InputStreamReader is = new InputStreamReader(
					process.getInputStream());
			final BufferedReader br = new BufferedReader(is);
			String line = br.readLine();
			while (line != null) {
				this.printToConsole(line);
				line = br.readLine();
			}
			final int exitCode = process.waitFor();
			assert exitCode == 0;
			br.close();
			is.close();
		}
		
		final List<String> imported = new ArrayList<String>();
		final List<String> metadataWritten = new ArrayList<String>();
		final List<String> errors = new ArrayList<String>();
		final List<String> previousImported = this
				.readFromPreviousImportedFiles();
		final List<String> previousMetadata = this
				.readFromPreviousMetadataFiles();

		final Map<String, Map<String, String>> keyValuesMap = this
				.readCSVMetadaFiles(rootDir);
		if (keyValuesMap == null) {
			final String error = "Process aborted by user";
			this.writeToLog(error + "\n");
			this.printToConsole(error);
			return;
		}

		Map<String, File> mmaFilesMap = null;
		if (this.hasMMAFiles) {
			mmaFilesMap = this.readMMAMetadaFiles(rootDir);
			if (mmaFilesMap == null) {
				final String error = "Process aborted by user";
				this.writeToLog(error + "\n");
				this.printToConsole(error);
				return;
			}
		}
		final LocalDateTime now = LocalDateTime.now();
		final int hour = now.getHour();

		// Read images and writes them in omero + metadata
		for (final File containerDir : rootDir.listFiles()) {
			final String containerName = containerDir.getName();
			if (!containerDir.isDirectory()) {
				continue;
			}
			for (final File projectDir : containerDir.listFiles()) {
				final String projectName = projectDir.getName();
				final String projectID = projectName.toLowerCase();
				if (!projectDir.isDirectory()) {
					if (projectName.endsWith(OmeroImporter.CSV_EXT)
							|| projectName.endsWith(OmeroImporter.JSON_EXT)) {
						if (!this.hasB2 && (this.destination != null)) {
							this.copyFile(this.destination, containerName, null,
									null, projectDir);
						}

					}
					continue;
				}
				Long projectId = null;
				try {
					projectId = this.retrieveProjectId(projectName);
				} catch (final DSOutOfServiceException ex) {
					final String error = "DSOutOfService error trying to retrieve ID for project "
							+ projectName + ", moving to next project.";
					this.writeToLog(error + "\n");
					this.writeToLog(ex.getMessage() + "\n");
					errors.add(error);
					continue;
				} catch (final DSAccessException ex) {
					final String error = "DSAccess error trying to retrieve ID for project "
							+ projectName + ", moving to next project.";
					this.writeToLog(error + "\n");
					this.writeToLog(projectName);
					this.writeToLog(ex.getMessage() + "\n");
					errors.add(error);
					continue;
				}
				if (projectId == -1L) {
					this.writeToLog("Project " + projectName
							+ " not found, creation started." + "\n");
					final Project project = new ProjectI();
					project.setName(omero.rtypes.rstring(projectName));
					// project.setDescription(omero.rtypes
					// .rstring("new description 1"));
					try {
						this.dataManager.saveAndReturnObject(this.ctx, project);
					} catch (final DSOutOfServiceException ex) {
						final String error = "DSOutOfService error trying to create project "
								+ projectName + ", moving to next project.";
						this.writeToLog(error + "\n");
						this.writeToLog(ex.getMessage() + "\n");
						errors.add(error);
						continue;
					} catch (final DSAccessException ex) {
						final String error = "DSAccess error trying to create project "
								+ projectName + ", moving to next project.";
						this.writeToLog(error + "\n");
						this.writeToLog(ex.getMessage() + "\n");
						errors.add(error);
						continue;
					}
					try {
						projectId = this.retrieveProjectId(projectName);
					} catch (final DSOutOfServiceException ex) {
						final String error = "DSOutOfService error trying to retrieve ID for newly created project "
								+ projectName + ", moving to next project.";
						this.writeToLog(error + "\n");
						this.writeToLog(ex.getMessage() + "\n");
						errors.add(error);
						continue;
					} catch (final DSAccessException ex) {
						final String error = "DSAccess error trying to retrieve ID for newly created project "
								+ projectName + ", moving to next project.";
						this.writeToLog(error + "\n");
						this.writeToLog(ex.getMessage() + "\n");
						errors.add(error);
						continue;
					}
					if (projectId == -1L) {
						final String error = "Unknown error during creation of project "
								+ projectName + ", moving to next project.";
						this.writeToLog(error + "\n");
						errors.add(error);
						continue;
					}
					this.writeToLog("Project " + projectName
							+ " successfully created" + "\n");
					final Map<String, String> projectMap = keyValuesMap
							.get(projectID);
					final String partialPath = projectDir.getAbsolutePath()
							.toString()
							.replace(this.target + File.separator, "");
					if (previousMetadata.contains(partialPath)) {
						this.writeToLog("Metadata for  " + projectName
								+ " previously written" + "\n");
					} else {
						if (projectMap != null) {
							try {
								this.dataWriter.writeDataToProject(projectId,
										"Project data", projectMap);
							} catch (final DSOutOfServiceException ex) {
								final String error = "DSAccess error trying to save metadata for project "
										+ projectName + ".";
								this.writeToLog(error + "\n");
								errors.add(error);
								this.writeToLog(ex.getMessage() + "\n");
							} catch (final DSAccessException ex) {
								final String error = "DSAccess error trying to save metadata for project "
										+ projectName + ".";
								this.writeToLog(error + "\n");
								this.writeToLog(ex.getMessage() + "\n");
								errors.add(error);
							}
							
							metadataWritten.add(partialPath);
							this.writeToLog(
									"Metadata for project " + projectName
											+ "successfully written" + "\n");
						} else {
							final String error = "Metadata for project "
									+ projectName + " not available.";
							this.writeToLog(error + "\n");
							errors.add(error);
						}
					}
				}

				for (final File datasetDir : projectDir.listFiles()) {
					final String datasetName = datasetDir.getName();
					final String datasetFullName = projectName + File.separator
							+ datasetName;
					final String datasetID = datasetFullName.toLowerCase();
					if (!datasetDir.isDirectory()) {
						if (datasetName.endsWith(OmeroImporter.CSV_EXT)
								|| datasetName
										.endsWith(OmeroImporter.JSON_EXT)) {
							if (!this.hasB2 && (this.destination != null)) {
								this.copyFile(this.destination, containerName,
										projectName, null, datasetDir);
							}
						}
						continue;
					}
					Long datasetId;
					try {
						datasetId = this.retrieveDatasetId(datasetName,
								projectName);
					} catch (final DSOutOfServiceException ex) {
						final String error = "DSOutOfService error trying to retrieve ID for dataset "
								+ datasetFullName + ", moving to next dataset.";
						this.writeToLog(error + "\n");
						this.writeToLog(ex.getMessage() + "\n");
						errors.add(error);
						continue;
					} catch (final DSAccessException ex) {
						final String error = "DSAccess error trying to retrieve ID for dataset "
								+ datasetFullName + ", moving to next dataset.";
						this.writeToLog(error + "\n");
						this.writeToLog(ex.getMessage() + "\n");
						errors.add(error);
						continue;
					}
					if (datasetId == -1L) {
						this.writeToLog("Dataset " + datasetFullName
								+ " not found, creation started." + "\n");
						final Dataset dataset = new DatasetI();
						dataset.setName(omero.rtypes.rstring(datasetName));
						// dataset.setDescription(omero.rtypes
						// .rstring("new description 1"));
						// this.dataManager.saveAndReturnObject(this.ctx,
						// dataset);
						final ProjectDatasetLink link = new ProjectDatasetLinkI();
						link.setChild(dataset);
						link.setParent(new ProjectI(projectId, false));
						try {
							this.dataManager.saveAndReturnObject(this.ctx,
									link);
						} catch (final DSOutOfServiceException ex) {
							final String error = "DSOutOfService error trying to create dataset "
									+ datasetFullName
									+ ", moving to next dataset.";
							this.writeToLog(error + "\n");
							this.writeToLog(ex.getMessage() + "\n");
							errors.add(error);
							continue;
						} catch (final DSAccessException ex) {
							final String error = "DSAccess error trying to create dataset "
									+ datasetFullName
									+ ", moving to next dataset.";
							this.writeToLog(error + "\n");
							this.writeToLog(ex.getMessage() + "\n");
							errors.add(error);
							continue;
						}
						try {
							datasetId = this.retrieveDatasetId(datasetName,
									projectName);
						} catch (final DSOutOfServiceException ex) {
							final String error = "DSOutOfService error trying to retrieve ID for newly created dataset "
									+ datasetFullName
									+ ", moving to next dataset.";
							this.writeToLog(error + "\n");
							this.writeToLog(ex.getMessage() + "\n");
							errors.add(error);
							continue;
						} catch (final DSAccessException ex) {
							final String error = "DSAccess error trying to retrieve ID for newly created dataset "
									+ datasetFullName
									+ ", moving to next dataset.";
							this.writeToLog(error + "\n");
							this.writeToLog(ex.getMessage() + "\n");
							errors.add(error);
							continue;
						}
						if (datasetId == -1L) {
							final String error = "Unknown error during creation of dataset "
									+ datasetFullName
									+ ", moving to next dataset.";
							this.writeToLog(error + "\n");
							errors.add(error);
							continue;
						}
						this.writeToLog("Dataset " + datasetFullName
								+ " successfully created" + "\n");
						final Map<String, String> datasetMap = keyValuesMap
								.get(datasetID);
						final String partialPath = datasetDir.getAbsolutePath()
								.toString()
								.replace(this.target + File.separator, "");
						if (previousMetadata.contains(partialPath)) {
							this.writeToLog("Metadata for  " + datasetFullName
									+ " previously written" + "\n");
						} else {
							if (datasetMap != null) {
								try {
									this.dataWriter.writeDataToDataset(
											datasetId, "Dataset data",
											datasetMap);
								} catch (final DSOutOfServiceException ex) {
									final String error = "DSAccess error trying to save metadata for dataset "
											+ datasetFullName + ".";
									this.writeToLog(error + "\n");
									this.writeToLog(ex.getMessage() + "\n");
									errors.add(error);
								} catch (final DSAccessException ex) {
									final String error = "DSAccess error trying to save metadata for dataset "
											+ datasetFullName + ".";
									this.writeToLog(error + "\n");
									this.writeToLog(ex.getMessage() + "\n");
									errors.add(error);
								}
								
								metadataWritten.add(partialPath);
								this.writeToLog("Metadata for dataset "
										+ datasetFullName
										+ " successfully written" + "\n");
							} else {
								final String error = "Metadata for dataset "
										+ datasetFullName + " not available.";
								this.writeToLog(error + "\n");
								errors.add(error);
							}
						}
					}

					// } else {
					// this.config.targetClass.set("omero.model.Dataset");
					// this.config.targetId.set(datasetId);
					// }

					this.config.targetClass.set("omero.model.Dataset");
					this.config.targetId.set(datasetId);
					for (final File image : datasetDir.listFiles()) {
						if (image.isDirectory()) {
							continue;
						}
						final String imageName = image.getName();
						final String imageFullName = datasetFullName
								+ File.separator + imageName;

						// TODO SHOULD CHECK IF IMAGE NAME IS ALREADY PRESENT?!
						if (!this.extFilter.isEmpty()
								&& !imageName.endsWith(this.extFilter)) {
							continue;
						}
						if (!this.nameFilter.isEmpty()
								&& !imageName.contains(this.nameFilter)) {
							continue;
						}

						if (imageName.endsWith(OmeroImporter.CSV_EXT)
								|| imageName.endsWith(OmeroImporter.JSON_EXT)) {
							if (!this.hasB2 && (this.destination != null)) {
								this.copyFile(this.destination, containerName,
										projectName, datasetName, image);
							}
							continue;
						}

						final String partialPath = image.getAbsolutePath()
								.toString()
								.replace(this.target + File.separator, "");
						
						if (imageName.endsWith(OmeroImporter.CSV_EXT)
								|| imageName.endsWith(OmeroImporter.JSON_EXT)) {
							continue;
						}
						
						if (!previousImported.contains(partialPath)) {
							this.writeToLog("File " + imageFullName
									+ " previously imported" + "\n");

							final List<String> paths = new ArrayList<String>();
							paths.add(image.getAbsolutePath());
							final String[] imagePaths = new String[paths
									.size()];
							paths.toArray(imagePaths);

							final ImportCandidates candidates = new ImportCandidates(
									this.reader, imagePaths, this.handler);
							// for (final String imagePath :
							// candidates.getPaths())
							// {
							// printToConsole("WARNING: " + imagePath +
							// " will be imported");
							// }
							this.reader.setMetadataOptions(
									new DefaultMetadataOptions(
											MetadataLevel.ALL));
							this.library.importCandidates(this.config,
									candidates);

							Long imageId = -1L;
							try {
								imageId = this.retrieveImageId(imageName,
										datasetName, projectName);
							} catch (final DSOutOfServiceException ex) {
								final String error = "DSOutOfService error trying to retrieve ID for newly imported image "
										+ imageFullName
										+ ", moving to next image.";
								this.writeToLog(error + "\n");
								this.writeToLog(ex.getMessage() + "\n");
								errors.add(error);
								continue;
							} catch (final DSAccessException ex) {
								final String error = "DSAccess error trying to retrieve ID for newly imported image "
										+ imageFullName
										+ ", moving to next image.";
								this.writeToLog(error + "\n");
								this.writeToLog(ex.getMessage() + "\n");
								errors.add(error);
								continue;
							}
							if (imageId != -1L) {
								imported.add(partialPath);
								this.writeToLog("Image " + imageFullName
										+ " successfully imported" + "\n");
							}
						}

						Long imageId = -1L;
						try {
							imageId = this.retrieveImageId(imageName,
									datasetName, projectName);
						} catch (final DSOutOfServiceException ex) {
							final String error = "DSOutOfService error trying to retrieve ID for image "
									+ imageFullName + ", moving to next image.";
							this.writeToLog(error + "\n");
							this.writeToLog(ex.getMessage() + "\n");
							errors.add(error);
							continue;
						} catch (final DSAccessException ex) {
							final String error = "DSAccess error trying to retrieve ID image "
									+ imageFullName + ", moving to next image.";
							this.writeToLog(error + "\n");
							this.writeToLog(ex.getMessage() + "\n");
							errors.add(error);
							continue;
						}
						if (imageId == -1L) {
							continue;
						}
						
						final int lastIndex = imageFullName.lastIndexOf(".");
						String imageFullNameNoExt = imageFullName;
						if (lastIndex != -1) {
							final String ext = imageFullName
									.substring(lastIndex);
							imageFullNameNoExt = imageFullName.replace(ext, "");
						}
						final String imageID = imageFullNameNoExt.toLowerCase();
						final String microscopeID = datasetID + File.separator
								+ "microscope";
						final Map<String, String> imageMap = keyValuesMap
								.get(imageID);

						if (previousMetadata.contains(partialPath)) {
							this.writeToLog("Metadata for  " + imageFullName
									+ " previously written" + "\n");
						} else {
							if (imageMap != null) {
								try {
									this.dataWriter.writeDataToImage(imageId,
											"Image data", imageMap);
									if (this.hasMMAFiles) {
										final File acquisitionSetting = mmaFilesMap
												.get(imageID);
										final File microscope = mmaFilesMap
												.get(microscopeID);
										this.dataWriter
												.writeFileAnnotationToImage(
														imageId, microscope);
										this.dataWriter
												.writeFileAnnotationToImage(
														imageId,
														acquisitionSetting);
									}
								} catch (final DSOutOfServiceException ex) {
									final String error = "DSAccess error trying to save metadata for image "
											+ imageFullName + ".";
									this.writeToLog(error + "\n");
									this.writeToLog(ex.getMessage() + "\n");
									errors.add(error);
								} catch (final DSAccessException ex) {
									final String error = "DSAccess error trying to save metadata for image "
											+ imageFullName + ".";
									this.writeToLog(error + "\n");
									this.writeToLog(ex.getMessage() + "\n");
									errors.add(error);
								} catch (final FileNotFoundException ex) {
									final String error = "FileNotFound error trying to save metadata for image "
											+ imageFullName + ".";
									this.writeToLog(error + "\n");
									this.writeToLog(ex.getMessage() + "\n");
									errors.add(error);
								} catch (final ServerError ex) {
									final String error = "ServerError error trying to save metadata for image "
											+ imageFullName + ".";
									this.writeToLog(error + "\n");
									this.writeToLog(ex.getMessage() + "\n");
									errors.add(error);
								} catch (final IOException ex) {
									final String error = "IO error trying to save metadata for image "
											+ imageFullName + ".";
									this.writeToLog(error + "\n");
									this.writeToLog(ex.getMessage() + "\n");
									errors.add(error);
								}
								metadataWritten.add(partialPath);
								this.writeToLog("Metadata for image "
										+ imageFullName
										+ " successfully written" + "\n");
							} else {
								final String error = "Metadata for image "
										+ imageFullName + " not available.";
								this.writeToLog(error + "\n");
								errors.add(error);
							}
						}
						if (!this.hasB2 && (this.destination != null)) {
							this.copyFile(this.destination, containerName,
									projectName, datasetName, image);
						}

						if (!OmeroImporter.IGNORE_TIME
								&& (hour < this.timeStart)
								&& (hour > this.timeEnd)) {
							final String error = "ERROR: local time reached time end while importing, application terminated.";
							this.writeToLog(error + "\n");
							this.printToConsole(error);
							if (this.emailTo != null) {
								this.sendErrorEmail(error);
							}
							this.writeToLog(
									"Local time reached 9AM while importing, force exit"
											+ "\n");
							this.writeToPreviousImportedFile(imported);
							this.writeToPreviousMetadataFile(metadataWritten);
							this.close();
							System.exit(1);
						}
					}
					// if (imagePaths.length == 0) {
					// this.writeToLog("No images to import in ");
					// this.writeToLog(datasetFullName);
					// this.writeToLog("\n");
					// continue;
					// }
				}
			}
		}

		if (this.hasB2) {
			this.writeToLog("Starting Backblaze backup" + "\n");
			String b2SyncCommand = null;
			if (this.destination != null) {
				b2SyncCommand = this.b2Command + " sync --excludeAllSymlinks \""
						+ this.target + "\" \"b2://" + this.b2BucketName + "/"
						+ this.destination + "\"";
			} else {
				b2SyncCommand = this.b2Command + " sync --excludeAllSymlinks \""
						+ this.target + "\" \"b2://" + this.b2BucketName + "\"";
			}
			this.writeToLog(b2SyncCommand + "\n");
			this.printToConsole(b2SyncCommand);
			final Process process = Runtime.getRuntime().exec(b2SyncCommand);
			final InputStreamReader is = new InputStreamReader(
					process.getInputStream());
			final BufferedReader br = new BufferedReader(is);
			String line = br.readLine();
			while (line != null) {
				this.printToConsole(line);
				line = br.readLine();
			}
			final int exitCode = process.waitFor();
			assert exitCode == 0;
			br.close();
			is.close();
			this.writeToLog("Finishing Backblaze backup" + "\n");
		}
		this.sendCompleteEmail(imported, metadataWritten, errors);
		this.writeToPreviousImportedFile(imported);
		this.writeToPreviousMetadataFile(metadataWritten);
	}
	
	// Part 1 or more, counting "Cancer Avatar * metadata" string in the file
	Map<String, Map<String, String>> readMetadataFromFileMultifile(
			final String id, final File f, final int part) {
		final Map<String, Map<String, String>> map = new LinkedHashMap<String, Map<String, String>>();
		FileReader fr = null;
		try {
			fr = new FileReader(f);
		} catch (final FileNotFoundException ex) {
			this.writeToLog(
					"File not found error opening file in OmeroImporter for "
							+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
			return null;
		}

		final BufferedReader br = new BufferedReader(fr);

		int counter = 0;
		String line = null;
		boolean isKeyLine = false;
		final List<String> keys = new ArrayList<String>();
		try {
			line = br.readLine();
			while (line != null) {
				if (line.matches(OmeroImporter.metadataIdent)) {
					isKeyLine = true;
					counter++;
					line = br.readLine();
					continue;
				}
				if (counter > part) {
					break;
				}
				final String tokens[] = line.split(",");
				if ((counter < part) || (tokens.length == 0)) {
					line = br.readLine();
					continue;
				}
				if (isKeyLine) {
					for (final String s : tokens) {
						keys.add(s);
					}
					isKeyLine = false;
				} else {
					final List<String> values = new ArrayList<String>();
					for (int i = 0; i < keys.size(); i++) {
						if ((tokens.length - 1) < i) {
							values.add("NA");
						} else {
							if ((tokens[i] == null) || tokens[i].equals("")
									|| tokens[i].equals(" ")) {
								values.add("NA");
							} else {
								values.add(tokens[i]);
							}
						}
					}
					final String imgName = tokens[0].toLowerCase();
					if (!imgName.equals("") && !imgName.equals(" ")) {
						final Map<String, String> imageMap = new LinkedHashMap<String, String>();
						for (int i = 1; i < keys.size(); i++) {
							imageMap.put(keys.get(i), values.get(i));
						}
						final int lastIndex = imgName.lastIndexOf(".");
						String nameNoExt = imgName;
						if (lastIndex != -1) {
							final String ext = imgName.substring(lastIndex);
							nameNoExt = imgName.replace(ext, "");
						}
						final String imageid = id + nameNoExt;
						map.put(imageid, imageMap);
					}
				}
				line = br.readLine();
			}
		} catch (final IOException ex) {
			this.writeToLog("Read line error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}

		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("IO error closing file in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
			return null;
		}

		return map;
	}

	// Part 1 or more, counting "Cancer Avatar * metadata" string in the file
	Map<String, String> readMetadataFromFile(final File f, final int part) {
		final Map<String, String> map = new LinkedHashMap<String, String>();
		FileReader fr = null;
		try {
			fr = new FileReader(f);
		} catch (final FileNotFoundException ex) {
			this.writeToLog(
					"File not found error opening file in OmeroImporter for "
							+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
			return null;
		}
		
		final BufferedReader br = new BufferedReader(fr);
		
		int counter = 0;
		String line = null;
		try {
			line = br.readLine();
			while (line != null) {
				if (line.matches(OmeroImporter.metadataIdent)) {
					counter++;
					line = br.readLine();
					continue;
				}
				if (counter > part) {
					break;
				}
				final String tokens[] = line.split(",");
				if ((counter < part) || (tokens.length < 2)
						|| (tokens[0].toLowerCase().equals("key"))) {
					line = br.readLine();
					continue;
				}
				final String key = tokens[0];
				final String value = tokens[1];
				map.put(key, value);
				line = br.readLine();
			}
		} catch (final IOException ex) {
			this.writeToLog("Read line error in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		
		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("IO error closing file in OmeroImporter for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
			return null;
		}
		
		return map;
	}
	
	public void copyFile(final String destination, final String containerName,
			final String projectName, final String datasetName, final File f) {
		String path = destination;
		if (containerName != null) {
			path += File.separator + containerName;
			final File containerDir = new File(path);
			if (!containerDir.exists()) {
				containerDir.mkdir();
			}
		}
		if (projectName != null) {
			path += File.separator + projectName;
			final File projectDir = new File(path);
			if (!projectDir.exists()) {
				projectDir.mkdir();
			}
		}
		if (datasetName != null) {
			path += File.separator + datasetName;
			final File datasetDir = new File(path);
			if (!datasetDir.exists()) {
				datasetDir.mkdir();
			}
		}

		final String newFilePath = path + File.separator + f.getName();
		try {
			Files.copy(Paths.get(f.getAbsolutePath()), Paths.get(newFilePath));
		} catch (final FileAlreadyExistsException ex) {
			this.writeToLog(
					"File already exists in destination, copy avoided : "
							+ f.getName() + "\n");
		} catch (final IOException ex) {
			this.writeToLog("IO error in OmeroImporter during file copy for "
					+ f.getName() + "\n");
			this.writeToLog(ex.getMessage() + "\n");
		}
		if (this.deleteFiles) {
			final File parentFolder = f.getParentFile();
			try {
				Files.delete(Paths.get(f.getAbsolutePath()));
			} catch (final IOException ex) {
				this.writeToLog(
						"IO error in OmeroImporter during file delete for "
								+ f.getName() + "\n");
				this.writeToLog(ex.getMessage() + "\n");
			}
			if (parentFolder.listFiles().length == 0) {
				parentFolder.delete();
			}
		}
	}

	public static void main(final String[] args) {
		String hostName = "localhost", port = "4064", userName = null,
				password = null;
		String target = null;
		String destination = null;
		boolean headless = false;
		boolean configFile = false;
		boolean deleteFiles = false;
		boolean hasMMAFiles = false;
		boolean hasB2 = false;
		String b2AppKeyId = null;
		String b2AppKey = null;
		String b2BucketName = null;
		String emailTo = null;
		String adminEmailTo = null;
		String timeStart = null;
		String timeEnd = null;
		if (args.length == 0) {
			System.out.println("-h for help");
		}
		if ((args.length == 1) && (args[0].equals("-h"))) {
			System.out.println("-cfg, to use config file to pass parameter");
			System.out.println("-H <hostName>, localhost by default");
			System.out.println("-P <port>, 4064 by default");
			System.out.println("-u <userName>");
			System.out.println("-p <password>");
			System.out.println(
					"-t <target>, target directory to launch the importer");
			System.out.println(
					"-d <destination>, destination directory where to move files after import (in this case if not specified copy does not happen) OR additional folder for backblaze backup");
			System.out.println("-hl, to avoid dialogs interaction");
			System.out.println("-del, to delete files after import and copy");
			System.out.println(
					"-mma, to add microscope and aquisition settings file");
			System.out.println(
					"-b2 <appKeyId:appKey:bucketName>, to use backblaze as destination for copy (conflict with -d)");
			System.out.println(
					"-ml <email address> to set up automatic email upon error or completion");
			System.out.println(
					"-aml <email address> to set up automatic email to admin upon error or completion");
			System.out.println(
					"-tf <start:end> to specify in which timeframe the app can run");
			System.exit(0);
		}

		final OmeroImporter importer = new OmeroImporter();
		importer.initLogFiles();
		importer.printToConsole("LOG FILE INIT");

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-cfg")) {
				configFile = true;
			}
			if (args[i].equals("-H")) {
				hostName = args[i + 1];
			}
			if (args[i].equals("-P")) {
				port = args[i + 1];
			}
			if (args[i].equals("-u")) {
				userName = args[i + 1];
			}
			if (args[i].equals("-p")) {
				password = args[i + 1];
			}
			if (args[i].equals("-t")) {
				target = args[i + 1];
			}
			if (args[i].equals("-d")) {
				destination = args[i + 1];
			}
			if (args[i].equals("-hl")) {
				headless = true;
			}
			if (args[i].equals("-del")) {
				deleteFiles = true;
			}
			if (args[i].equals("-mma")) {
				hasMMAFiles = true;
			}
			if (args[i].equals("-b2")) {
				hasB2 = true;
				final String b2Data = args[i + 1];
				final String[] b2DataSplit = b2Data.split(":");
				if ((b2DataSplit.length < 3) || (b2DataSplit.length > 3)) {
					final String error = "wrong number of arguments in -b2 parameter, application terminated.";
					importer.writeToLog("ERROR: " + error + "\n");
					importer.printToConsole("ERROR: " + error);
					if (emailTo != null) {
						importer.sendErrorEmail(error);
					}
					System.exit(1);
				}
				b2AppKeyId = b2DataSplit[0];
				b2AppKey = b2DataSplit[1];
				b2BucketName = b2DataSplit[2];
			}
			if (args[i].equals("-ml")) {
				emailTo = args[i + 1];
			}
			if (args[i].equals("-aml")) {
				adminEmailTo = args[i + 1];
			}
			if (args[i].equals("-tf")) {
				final String tfData = args[i + 1];
				final String[] tfDataSplit = tfData.split(":");
				if ((tfDataSplit.length < 2) || (tfDataSplit.length > 2)) {
					final String error = "wrong number of arguments in -tf parameter, application terminated.";
					importer.writeToLog("ERROR: " + error + "\n");
					importer.printToConsole("ERROR: " + error);
					if (emailTo != null) {
						importer.sendErrorEmail(error);
					}
					System.exit(1);
				}
				timeStart = tfDataSplit[0];
				timeEnd = tfDataSplit[1];
			}

		}

		if (configFile) {
			final Map<String, String> parameters = importer.readConfigFile();
			if (parameters == null) {
				final String error = "reading parameters from config file failed in OmeroImporter, application terminated.";
				importer.writeToLog("ERROR: " + error + "\n");
				importer.printToConsole("ERROR: " + error);
				if (emailTo != null) {
					importer.sendErrorEmail(error);
				}
				System.exit(1);
			}

			for (final String key : parameters.keySet()) {
				if (key.startsWith("#")) {
					continue;
				}
				final String value = parameters.get(key);
				if (key.equals("hostname")) {
					hostName = value;
				}
				if (key.equals("port")) {
					port = value;
				}
				if (key.equals("username")) {
					userName = value;
				}
				if (key.equals("password")) {
					password = value;
				}
				if (key.equals("target")) {
					target = value;
				}
				if (key.equals("destination")) {
					destination = value;
				}
				if (key.equals("headless")) {
					headless = Boolean.valueOf(value);
				}
				if (key.equals("delete")) {
					deleteFiles = Boolean.valueOf(value);
				}
				if (key.equals("mma")) {
					hasMMAFiles = Boolean.valueOf(value);
				}
				if (key.equals("b2")) {
					hasB2 = true;
					final String b2Data = String.valueOf(value);
					final String[] b2DataSplit = b2Data.split(":");
					b2AppKeyId = b2DataSplit[0];
					b2AppKey = b2DataSplit[1];
					b2BucketName = b2DataSplit[2];
				}
				if (key.equals("email")) {
					emailTo = value;
				}
				if (key.equals("admin-email")) {
					adminEmailTo = value;
				}
				if (key.equals("timeframe")) {
					final String tfData = String.valueOf(value);
					final String[] tfDataSplit = tfData.split(":");
					timeStart = tfDataSplit[0];
					timeEnd = tfDataSplit[1];
				}
			}
			importer.printToConsole("CONFIG INIT");
		}
		if (hostName == null) {
			final String error = "hostName must be set, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}
		if (port == null) {
			final String error = "port has not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}
		if (userName == null) {
			final String error = "userName has not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);

		}
		if (password == null) {
			final String error = "password has not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}
		if (target == null) {
			final String error = "target has not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}

		// if (destination == null) {
		// final String error = "destination has not been specified, application
		// terminated.";
		// importer.writeToLog("ERROR: " + error + "\n");
		// importer.printToConsole("ERROR: " + error);
		// if (emailTo != null) {
		// importer.sendErrorEmail(error);
		// }
		// System.exit(1);
		// }

		if (hasB2 && ((b2AppKeyId == null) || (b2AppKey == null)
				|| (b2BucketName == null))) {
			final String error = "set has Backblaze backup but some bucket information have not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}

		if ((timeStart == null) || (timeEnd == null)) {
			final String error = "start and end time have not been specified.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		} else {

		}

		Integer timeStartI = null;
		Integer timeEndI = null;
		try {
			timeStartI = Integer.valueOf(port);
		} catch (final Exception ex) {
			final String error = "time start is not a valid number, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.writeToLog(ex.getMessage() + "\n");
			importer.printToConsole("ERROR: " + error);
			importer.printToConsole(ex.getMessage());
			if (emailTo != null) {
				importer.sendErrorEmail(error + "\n" + ex.getMessage());
			}
			System.exit(1);
		}
		try {
			timeEndI = Integer.valueOf(port);
		} catch (final Exception ex) {
			final String error = "time end is not a valid number, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.writeToLog(ex.getMessage() + "\n");
			importer.printToConsole("ERROR: " + error);
			importer.printToConsole(ex.getMessage());
			if (emailTo != null) {
				importer.sendErrorEmail(error + "\n" + ex.getMessage());
			}
			System.exit(1);
		}
		
		importer.setTarget(target);
		importer.setDestination(destination);
		importer.setDeleteFiles(deleteFiles);
		importer.setHeadless(headless);
		importer.setHasMMAFiles(hasMMAFiles);
		importer.setB2Data(hasB2, b2AppKeyId, b2AppKey, b2BucketName);
		importer.setEmailTo(emailTo);
		importer.setAdminEmailTo(adminEmailTo);
		importer.setTimeframe(timeStartI, timeEndI);

		if (userName == null) {
			final String error = "omero Username has not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}
		if (password == null) {
			final String error = "omero Password has not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}

		if (port == null) {
			final String error = "omero port has not been specified, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}

		Integer portI = null;
		try {
			portI = Integer.valueOf(port);
		} catch (final Exception ex) {
			final String error = "port is not a valid number, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.writeToLog(ex.getMessage() + "\n");
			importer.printToConsole("ERROR: " + error);
			importer.printToConsole(ex.getMessage());
			if (emailTo != null) {
				importer.sendErrorEmail(error + "\n" + ex.getMessage());
			}
			System.exit(1);
		}

		final File f = new File(target);
		if (!f.exists()) {
			final String error = "target directory doesn't exists, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}
		if (!f.isDirectory()) {
			final String error = "target directory is not a directory, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.printToConsole("ERROR: " + error);
			if (emailTo != null) {
				importer.sendErrorEmail(error);
			}
			System.exit(1);
		}

		if (destination != null) {
			final File f2 = new File(destination);
			if (!hasB2 && !f2.exists()) {
				final String error = "destination directory doesn't exists, application terminated.";
				importer.writeToLog("ERROR: " + error + "\n");
				importer.printToConsole("ERROR: " + error);
				if (emailTo != null) {
					importer.sendErrorEmail(error);
				}
				System.exit(1);
			}
			if (!hasB2 && !f2.isDirectory()) {
				final String error = "destination directory is not a directory, application terminated.";
				importer.writeToLog("ERROR: " + error + "\n");
				importer.printToConsole("ERROR: " + error);
				if (emailTo != null) {
					importer.sendErrorEmail(error);
				}
				System.exit(1);
			}
		}

		importer.setServerAccessInformation(hostName, portI, userName,
				password);

		importer.printToConsole("SERVER CONFIG INIT");

		try {
			importer.init();
		} catch (final Exception ex) {
			final String error = "something went wrong during OmeroImporter initialization, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.writeToLog(ex.getMessage() + "\n");
			importer.printToConsole("ERROR: " + error);
			importer.printToConsole(ex.getMessage());
			if (emailTo != null) {
				importer.sendErrorEmail(error + "\n" + ex.getMessage());
			}
			importer.close();
			System.exit(1);
		}
		
		importer.printToConsole("IMPORTER INIT");

		try {
			importer.importImages();
		} catch (final IOException ex) {
			final String error = "something went wrong during OmeroImporter import, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.writeToLog(ex.getMessage() + "\n");
			importer.printToConsole("ERROR: " + error);
			importer.printToConsole(ex.getMessage());
			if (emailTo != null) {
				importer.sendErrorEmail(error + "\n" + ex.getMessage());
			}
			importer.close();
			System.exit(1);
		} catch (final InterruptedException ex) {
			final String error = "something went wrong during OmeroImporter import, application terminated.";
			importer.writeToLog("ERROR: " + error + "\n");
			importer.writeToLog(ex.getMessage() + "\n");
			importer.printToConsole("ERROR: " + error);
			importer.printToConsole(ex.getMessage());
			if (emailTo != null) {
				importer.sendErrorEmail(error + "\n" + ex.getMessage());
			}
			importer.close();
			System.exit(1);
		}
		importer.printToConsole("IMAGES IMPORT DONE");

		importer.close();
	}

	private void setHeadless(final boolean headless) {
		this.headless = headless;
	}

	private void setTarget(final String target) {
		this.target = target;
	}
	
	private void setDestination(final String destination) {
		this.destination = destination;
	}
	
	private void setDeleteFiles(final boolean deleteFiles) {
		this.deleteFiles = deleteFiles;
	}
	
	private void setHasMMAFiles(final boolean hasMMAFiles) {
		this.hasMMAFiles = hasMMAFiles;
	}
	
	private void setB2Data(final boolean hasB2, final String b2AppKeyId,
			final String b2AppKey, final String b2BucketName) {
		this.hasB2 = hasB2;
		this.b2AppKeyId = b2AppKeyId;
		this.b2AppKey = b2AppKey;
		this.b2BucketName = b2BucketName;
	}
	
	private void setEmailTo(final String emailTo) {
		this.emailTo = emailTo;
	}
	
	private void setAdminEmailTo(final String adminEmailTo) {
		this.adminEmailTo = adminEmailTo;
	}

	private void setTimeframe(final int timeStart, final int timeEnd) {
		this.timeStart = timeStart;
		this.timeEnd = timeEnd;
	}
}
