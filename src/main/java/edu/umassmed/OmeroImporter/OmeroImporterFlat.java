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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataLevel;
import ome.formats.OMEROMetadataStoreClient;
import ome.formats.importer.ImportCandidates;
import ome.formats.importer.ImportConfig;
import ome.formats.importer.ImportLibrary;
import ome.formats.importer.OMEROWrapper;
import ome.formats.importer.cli.ErrorHandler;
import ome.formats.importer.cli.LoggingImportMonitor;
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
import edu.umassmed.OmeroDataWriter.OmeroDataWriter;

// TODO eliminate import / metadata written if previously imported
// add previously imported instead
// present better error if copy files failed cuz its already there

public class OmeroImporterFlat {
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
	private static String configFileName = "OmeroImporter.cfg";

	private File outputLogFile, outputMetadataLogFile, outputImportedFile;

	private boolean metadataLogFileNeeded;

	private boolean headless, deleteFiles;

	private JFrame hiddenFrame;

	private static String metadataIdent = "Cancer Avatar \\S+ metadata,";
	private final static String DATE_FORMAT = "yyyy-MM-dd_hh-mm-ss";

	public OmeroImporterFlat() {

		this.headless = false;
		this.deleteFiles = false;

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
		final File f = new File(OmeroImporterFlat.configFileName);

		FileReader fr;
		BufferedReader br;
		try {
			fr = new FileReader(f);
			br = new BufferedReader(fr);
		} catch (final FileNotFoundException ex) {
			this.writeToLog("Opening file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.configFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
			return null;
		}

		try {
			String line = br.readLine();
			while (line != null) {
				final String[] tokens = line.split("\t");
				parameters.put(tokens[0], tokens[1]);
				line = br.readLine();
			}
		} catch (final IOException ex) {
			this.writeToLog("Reading line error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.configFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
			return null;
		}
		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.configFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
			return null;
		}
		return parameters;
	}

	public Long retrieveImageId(final String imageName,
			final String datasetName, final String projectName)
			throws DSOutOfServiceException, DSAccessException {
		List<Long> datasetIDs = this.retrieveDatasetIDsFromProject(projectName);
		final Collection<DatasetData> datasets = this.browser.getDatasets(
				this.ctx, datasetIDs);

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
		final Collection<ImageData> images = this.browser.getImagesForDatasets(
				this.ctx, datasetIDs);

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
			final String projectName) throws DSOutOfServiceException,
			DSAccessException {
		final List<Long> datasetIDs = this
				.retrieveDatasetIDsFromProject(projectName);
		final Collection<DatasetData> datasets = this.browser.getDatasets(
				this.ctx, datasetIDs);

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
		final ExperimenterData experimenter = this.admin.lookupExperimenter(
				this.ctx, userName);
		if (experimenter == null)
			return -1L;
		return experimenter.getId();
	}

	public void close() {
		if (!this.metadataLogFileNeeded) {
			this.outputMetadataLogFile.delete();
		}
		this.hiddenFrame.dispose();
		this.dataWriter.close();
		this.gateway.disconnect();
		this.store.logout();
	}

	private void initLogFiles() {
		final GregorianCalendar cal = new GregorianCalendar();
		
		final SimpleDateFormat format = new SimpleDateFormat(
				OmeroImporterFlat.DATE_FORMAT);
		final String date = format.format(cal.getTime());
		
		final String path = System.getProperty("user.dir") + File.separator;
		
		final String outputLogFileName = path + date + "_"
				+ OmeroImporterFlat.outputLogFileName;
		this.outputLogFile = new File(outputLogFileName);
		try {
			this.outputLogFile.createNewFile();
		} catch (final IOException ex) {
			ex.printStackTrace();
			System.out.println("OmeroImporter - creating log file failed");
		}
		
		final String outputImportedFileName = path + date + "_"
				+ OmeroImporterFlat.outputImportedFileName;
		this.outputImportedFile = new File(outputImportedFileName);
		try {
			this.outputImportedFile.createNewFile();
		} catch (final IOException ex) {
			this.writeToLog("Creating file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputImportedFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}
		
		final String outputMetadataLogFileName = path + date + "_"
				+ OmeroImporterFlat.outputMetadataLogFileName;
		this.outputMetadataLogFile = new File(outputMetadataLogFileName);
		try {
			this.outputMetadataLogFile.createNewFile();
		} catch (final IOException ex) {
			this.writeToLog("Creating file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputMetadataLogFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}
		
		this.writeToLog(date);
		this.writeToLog(" - OmeroImporter started\n");
	}

	private void writeToMetadataLogAndLog(final String s) {
		this.metadataLogFileNeeded = true;
		this.writeToLog(s);
		FileWriter fw = null;
		try {
			fw = new FileWriter(this.outputMetadataLogFile, true);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputMetadataLogFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}
		final BufferedWriter bw = new BufferedWriter(fw);

		try {
			if (s == null) {
				bw.write("No Message");
			} else {
				bw.write(s);
			}
		} catch (final IOException ex) {
			this.writeToLog("Write line error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputMetadataLogFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}

		try {
			bw.close();
			fw.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputMetadataLogFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}
	}

	private void writeToLog(final String s) {
		FileWriter fw = null;
		try {
			fw = new FileWriter(this.outputLogFile, true);
		} catch (final IOException ex) {
			ex.printStackTrace();
			System.out.println("OmeroImporter - opening log file failed");
		}
		final BufferedWriter bw = new BufferedWriter(fw);

		try {
			if (s == null) {
				bw.write("No Message");
			} else {
				bw.write(s);
			}
		} catch (final IOException ex) {
			ex.printStackTrace();
			System.out.println("OmeroImporter - writing log file failed");
		}

		try {
			bw.close();
			fw.close();
		} catch (final IOException ex) {
			ex.printStackTrace();
			System.out.println("OmeroImporter - closing log file failed");
		}

	}

	private void writeToPreviouslyImportedFile(final List<String> imported) {
		FileWriter fw = null;
		try {
			fw = new FileWriter(this.outputImportedFile, true);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputImportedFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
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
			this.writeToLog("Write line error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputImportedFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}

		try {
			bw.close();
			fw.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputImportedFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}
	}

	private List<String> readFromPreviouslyImportedFiles() {
		final String path = System.getProperty("user.dir");
		final List<String> previouslyImported = new ArrayList<String>();
		final File dir = new File(path);
		for (final File f : dir.listFiles()) {
			if (!f.getName().contains(OmeroImporterFlat.outputImportedFileName)
					|| f.getName().equals(this.outputImportedFile.getName())) {
				continue;
			}
			final List<String> tmpPreviouslyImported = this
					.readFromPreviouslyImportedFile(f);
			previouslyImported.addAll(tmpPreviouslyImported);
		}
		return previouslyImported;
	}
	
	private List<String> readFromPreviouslyImportedFile(final File f) {
		// final File f = new File(OmeroImporterFlat.outputImportedFileName);
		final List<String> previouslyImported = new ArrayList<String>();
		if (!f.exists())
			return previouslyImported;
		FileReader fr = null;
		try {
			fr = new FileReader(f);
		} catch (final IOException ex) {
			this.writeToLog("Opening file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputImportedFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
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
				previouslyImported.add(value);
				line = br.readLine();
			}
		} catch (final IOException ex) {
			this.writeToLog("Read line error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputImportedFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}

		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("Closing file error in OmeroImporter for ");
			this.writeToLog(OmeroImporterFlat.outputImportedFileName);
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}
		return previouslyImported;
	}

	private Map<String, Map<String, String>> readCSVMetadaFiles(
			final File rootDir) {
		final Map<String, Map<String, String>> keyValuesMap = new LinkedHashMap<String, Map<String, String>>();
		final List<String> csvNecessary = new ArrayList<String>();
		// Read csv metadata files first and collect data
		for (final File projectDataset : rootDir.listFiles()) {
			if (projectDataset.isDirectory()) {
				csvNecessary.add(projectDataset.getName());
				for (final File image : projectDataset.listFiles()) {
					if (image.isDirectory() || image.getName().endsWith(".csv")) {
						continue;
					}
					csvNecessary.add(image.getName());
				}
			}
		}
		for (final File projectDataset : rootDir.listFiles()) {
			if (projectDataset.isDirectory()) {
				for (final File image : projectDataset.listFiles()) {
					if (image.isDirectory()) {
						continue;
					}
					if (!image.getName().endsWith(".csv")) {
						continue;
					}
					if (image.getName().contains(projectDataset.getName())) {
						final String projectDatasetName = projectDataset
								.getName();
						final int projectEndIndex = projectDatasetName.indexOf(
								".", projectDatasetName.indexOf(".") + 1);
						final String projectName = projectDatasetName
								.substring(0, projectEndIndex);
						Map<String, String> projectMap = null;
						projectMap = this.readMetadataFromFile(image, 1);
						if (projectMap == null) {
							this.writeToMetadataLogAndLog("Error during reading of metadata for ");
							this.writeToMetadataLogAndLog(projectName);
							this.writeToMetadataLogAndLog("\n");
							continue;
						}
						if (projectMap.isEmpty()) {
							this.writeToMetadataLogAndLog("Read empty metadata for ");
							this.writeToMetadataLogAndLog(projectName);
							this.writeToMetadataLogAndLog("\n");
							continue;
						}
						keyValuesMap.put(projectName, projectMap);
						this.writeToLog("Successfully read metadata for ");
						this.writeToLog(projectName);
						this.writeToLog("\n");

						Map<String, String> datasetMap = null;
						datasetMap = this.readMetadataFromFile(image, 2);
						if (datasetMap == null) {
							this.writeToMetadataLogAndLog("Error during reading of metadata for ");
							this.writeToMetadataLogAndLog(projectDatasetName);
							this.writeToMetadataLogAndLog("\n");
							continue;
						}
						if (datasetMap.isEmpty()) {
							this.writeToMetadataLogAndLog("Read empty metadata for ");
							this.writeToMetadataLogAndLog(projectDatasetName);
							this.writeToMetadataLogAndLog("\n");
							continue;
						}
						keyValuesMap.put(projectDatasetName, datasetMap);
						this.writeToLog("Successfully read metadata for ");
						this.writeToLog(projectDatasetName);
						this.writeToLog("\n");
						csvNecessary.remove(projectDatasetName);
					} else {
						Map<String, String> imageMap = null;
						imageMap = this.readMetadataFromFile(image, 1);
						final String partialPath = image
								.getAbsolutePath()
								.toString()
								.replace(
										rootDir.getAbsolutePath()
												+ File.separator, "");
						if (imageMap == null) {
							this.writeToMetadataLogAndLog("Error during reading of metadata for ");
							this.writeToMetadataLogAndLog(partialPath);
							this.writeToMetadataLogAndLog("\n");
							continue;
						}
						if (imageMap.isEmpty()) {
							this.writeToMetadataLogAndLog("Read empty metadata for ");
							this.writeToMetadataLogAndLog(partialPath);
							this.writeToMetadataLogAndLog("\n");
							continue;
						}
						final String name = image.getName().substring(0,
								image.getName().lastIndexOf("."));
						keyValuesMap.put(name, imageMap);
						csvNecessary.remove(name);
						this.writeToLog("Successfully read metadata for ");
						this.writeToLog(partialPath);
						this.writeToLog("\n");
					}
				}
			}
			// if (!projectDataset.getName().endsWith(".csv")) {
			// continue;
			// }
			// Map<String, String> projectMap = null;
			// projectMap = this.readMetadataFromFile(projectDataset);
			// if (projectMap == null) {
			// this.writeToLog("Error during reading of metadata for ");
			// this.writeToLog(projectDataset.getName());
			// this.writeToLog("\n");
			// continue;
			// }
			// final String name = projectDataset.getName().substring(0,
			// projectDataset.getName().lastIndexOf("."));
			// keyValuesMap.put(name, projectMap);
			// csvNecessary.remove(name);
			// this.writeToLog("Successfully read metadata for ");
			// this.writeToLog(projectDataset.getName());
			// this.writeToLog("\n");
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
			final int response = JOptionPane.showConfirmDialog(
					this.hiddenFrame, message, "Warning metadata missing",
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

	private void importImages(final String targetPath,
			final String destinationPath) {
		final File rootDir = new File(targetPath);

		// if (!rootDir.isDirectory()) {
		// System.out.println(targetPath + " is not a valid directory");
		// return;
		// }

		final List<String> imported = new ArrayList<String>();
		final List<String> previouslyImported = this
				.readFromPreviouslyImportedFiles();

		final Map<String, Map<String, String>> keyValuesMap = this
				.readCSVMetadaFiles(rootDir);
		if (keyValuesMap == null) {
			this.writeToLog("Process aborted by user");
			this.writeToLog("\n");
			return;
		}

		// Read images and writes them in omero + metadata
		for (final File projectDatasetDir : rootDir.listFiles()) {
			if (!projectDatasetDir.isDirectory()) {
				continue;
			}
			final String projectDatasetName = projectDatasetDir.getName();
			final int projectEndIndex = projectDatasetName.indexOf(".",
					projectDatasetName.indexOf(".") + 1);
			final String projectName = projectDatasetName.substring(0,
					projectEndIndex);
			Long projectId = null;
			try {
				projectId = this.retrieveProjectId(projectName);
			} catch (final DSOutOfServiceException ex) {
				this.writeToLog("DSOutOfService error trying to retrieve ID for project ");
				this.writeToLog(projectName);
				this.writeToLog(", moving to next project");
				this.writeToLog("\n");
				this.writeToLog(ex.toString());
				this.writeToLog("\n");
				continue;
			} catch (final DSAccessException ex) {
				this.writeToLog("DSAccess error trying to retrieve ID for project ");
				this.writeToLog(projectName);
				this.writeToLog(", moving to next project");
				this.writeToLog("\n");
				this.writeToLog(ex.toString());
				this.writeToLog("\n");
				continue;
			}
			if (projectId == -1L) {
				this.writeToLog("Project ");
				this.writeToLog(projectName);
				this.writeToLog(" not found, creation started");
				this.writeToLog("\n");
				final Project project = new ProjectI();
				project.setName(omero.rtypes.rstring(projectName));
				// project.setDescription(omero.rtypes
				// .rstring("new description 1"));
				try {
					this.dataManager.saveAndReturnObject(this.ctx, project);
				} catch (final DSOutOfServiceException ex) {
					this.writeToLog("DSOutOfService error trying to create project ");
					this.writeToLog(projectName);
					this.writeToLog(", moving to next project");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				} catch (final DSAccessException ex) {
					this.writeToLog("DSAccess error trying to create project ");
					this.writeToLog(projectName);
					this.writeToLog(", moving to next project");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				}
				try {
					projectId = this.retrieveProjectId(projectName);
				} catch (final DSOutOfServiceException ex) {
					this.writeToLog("DSOutOfService error trying to retrieve ID for newly created project ");
					this.writeToLog(projectName);
					this.writeToLog(", moving to next project");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				} catch (final DSAccessException ex) {
					this.writeToLog("DSAccess error trying to retrieve ID for newly created project ");
					this.writeToLog(projectName);
					this.writeToLog(", moving to next project");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				}
				if (projectId == -1L) {
					this.writeToLog("Unknown error during creation of project ");
					this.writeToLog(projectName);
					this.writeToLog("\n");
					continue;
				}
				this.writeToLog("Project ");
				this.writeToLog(projectName);
				this.writeToLog(" successfully created");
				this.writeToLog("\n");
				final Map<String, String> projectMap = keyValuesMap
						.get(projectName);
				if (projectMap != null) {
					try {
						this.dataWriter.writeDataToProject(projectId,
								"Project data", projectMap);
					} catch (final DSOutOfServiceException ex) {
						this.writeToLog("DSAccess error trying to save metadata for project ");
						this.writeToLog(projectName);
						this.writeToLog("\n");
						this.writeToLog(ex.toString());
						this.writeToLog("\n");
					} catch (final DSAccessException ex) {
						this.writeToLog("DSAccess error trying to save metadata for project ");
						this.writeToLog(projectName);
						this.writeToLog("\n");
						this.writeToLog(ex.toString());
						this.writeToLog("\n");
					}
					this.writeToLog("Metadata for project ");
					this.writeToLog(projectName);
					this.writeToLog(" successfully written");
					this.writeToLog("\n");
				} else {
					this.writeToLog("Metadata for project ");
					this.writeToLog(projectName);
					this.writeToLog(" not available");
					this.writeToLog("\n");
				}
			}

			final String datasetName = projectDatasetName;
			Long datasetId;
			try {
				datasetId = this.retrieveDatasetId(datasetName, projectName);
			} catch (final DSOutOfServiceException ex) {
				this.writeToLog("DSOutOfService error trying to retrieve ID for dataset ");
				this.writeToLog(datasetName);
				this.writeToLog(", moving to next dataset");
				this.writeToLog("\n");
				this.writeToLog(ex.toString());
				this.writeToLog("\n");
				continue;
			} catch (final DSAccessException ex) {
				this.writeToLog("DSAccess error trying to retrieve ID for dataset ");
				this.writeToLog(datasetName);
				this.writeToLog(", moving to next dataset");
				this.writeToLog("\n");
				this.writeToLog(ex.toString());
				this.writeToLog("\n");
				continue;
			}
			if (datasetId == -1L) {
				final Dataset dataset = new DatasetI();
				dataset.setName(omero.rtypes.rstring(datasetName));
				// dataset.setDescription(omero.rtypes
				// .rstring("new description 1"));
				// this.dataManager.saveAndReturnObject(this.ctx, dataset);
				final ProjectDatasetLink link = new ProjectDatasetLinkI();
				link.setChild(dataset);
				link.setParent(new ProjectI(projectId, false));
				try {
					this.dataManager.saveAndReturnObject(this.ctx, link);
				} catch (final DSOutOfServiceException ex) {
					this.writeToLog("DSOutOfService error trying to create dataset ");
					this.writeToLog(datasetName);
					this.writeToLog(", moving to next dataset");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				} catch (final DSAccessException ex) {
					this.writeToLog("DSAccess error trying to create dataset ");
					this.writeToLog(datasetName);
					this.writeToLog(", moving to next dataset");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				}
				try {
					datasetId = this
							.retrieveDatasetId(datasetName, projectName);
				} catch (final DSOutOfServiceException ex) {
					this.writeToLog("DSOutOfService error trying to retrieve ID for newly created dataset ");
					this.writeToLog(datasetName);
					this.writeToLog(", moving to next dataset");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				} catch (final DSAccessException ex) {
					this.writeToLog("DSAccess error trying to retrieve ID for newly created dataset ");
					this.writeToLog(datasetName);
					this.writeToLog(", moving to next dataset");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				}
				if (datasetId == -1L) {
					this.writeToLog("Unknown error during creation of dataset ");
					this.writeToLog(datasetName);
					this.writeToLog("\n");
					continue;
				}
				this.writeToLog("Dataset ");
				this.writeToLog(datasetName);
				this.writeToLog(" successfully created");
				this.writeToLog("\n");
				final Map<String, String> datasetMap = keyValuesMap
						.get(datasetName);
				if (datasetMap != null) {
					try {
						this.dataWriter.writeDataToDataset(datasetId,
								"Dataset data", datasetMap);
					} catch (final DSOutOfServiceException ex) {
						this.writeToLog("DSAccess error trying to save metadata for dataset ");
						this.writeToLog(datasetName);
						this.writeToLog("\n");
						this.writeToLog(ex.toString());
						this.writeToLog("\n");
					} catch (final DSAccessException ex) {
						this.writeToLog("DSAccess error trying to save metadata for dataset ");
						this.writeToLog(datasetName);
						this.writeToLog("\n");
						this.writeToLog(ex.toString());
						this.writeToLog("\n");
					}
					this.writeToLog("Metadata for dataset ");
					this.writeToLog(datasetName);
					this.writeToLog(" successfully written");
					this.writeToLog("\n");
				} else {
					this.writeToLog("Metadata for dataset ");
					this.writeToLog(datasetName);
					this.writeToLog(" not available");
					this.writeToLog("\n");
				}
			}
			// } else {
			// this.config.targetClass.set("omero.model.Dataset");
			// this.config.targetId.set(datasetId);
			// }

			this.config.targetClass.set("omero.model.Dataset");
			this.config.targetId.set(datasetId);

			final List<String> paths = new ArrayList<String>();
			for (final File image : projectDatasetDir.listFiles()) {
				if (image.isDirectory()) {
					continue;
				}
				final String imageName = image.getName();
				// TODO SHOULD CHECK IF IMAGE NAME IS ALREADY PRESENT?!
				if (!this.extFilter.isEmpty()
						&& !imageName.endsWith(this.extFilter)) {
					continue;
				}
				if (!this.nameFilter.isEmpty()
						&& !imageName.contains(this.nameFilter)) {
					continue;
				}

				if (imageName.endsWith(".csv")) {
					continue;
				}

				final String partialPath = image.getAbsolutePath().toString()
						.replace(targetPath + File.separator, "");
				if (previouslyImported.contains(partialPath)) {
					this.writeToLog("Image ");
					this.writeToLog(partialPath);
					this.writeToLog(" previously imported");
					this.writeToLog("\n");
					continue;
				}

				imported.add(partialPath);
				paths.add(image.getAbsolutePath());
			}

			final String[] imagePaths = new String[paths.size()];
			paths.toArray(imagePaths);

			final ImportCandidates candidates = new ImportCandidates(
					this.reader, imagePaths, this.handler);
			// for (final String imagePath : candidates.getPaths()) {
			// System.out.println("WARNING: " + imagePath +
			// " will be imported");
			// }
			this.reader.setMetadataOptions(new DefaultMetadataOptions(
					MetadataLevel.ALL));
			this.library.importCandidates(this.config, candidates);

			for (final File image : projectDatasetDir.listFiles()) {
				if (image.isDirectory()) {
					continue;
				}

				final String imageName = image.getName();

				if (!this.extFilter.isEmpty()
						&& !imageName.endsWith(this.extFilter)) {
					continue;
				}
				if (!this.nameFilter.isEmpty()
						&& !imageName.contains(this.nameFilter)) {
					continue;
				}

				if (imageName.endsWith(".csv")) {
					this.copyFile(destinationPath, projectDatasetName, image);
					continue;
				}

				final String partialPath = image.getAbsolutePath().toString()
						.replace(targetPath + File.separator, "");
				if (previouslyImported.contains(partialPath)) {
					continue;
				}

				Long imageId = -1L;
				try {
					imageId = this.retrieveImageId(imageName, datasetName,
							projectName);
				} catch (final DSOutOfServiceException ex) {
					this.writeToLog("DSOutOfService error trying to retrieve ID for newly created image ");
					this.writeToLog(imageName);
					this.writeToLog(", moving to next image");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				} catch (final DSAccessException ex) {
					this.writeToLog("DSAccess error trying to retrieve ID for newly created image ");
					this.writeToLog(imageName);
					this.writeToLog(", moving to next image");
					this.writeToLog("\n");
					this.writeToLog(ex.toString());
					this.writeToLog("\n");
					continue;
				}
				if (imageId == -1L) {
					continue;
				}

				this.writeToLog("Image ");
				this.writeToLog(imageName);
				this.writeToLog(" successfully imported");
				this.writeToLog("\n");

				final Map<String, String> imageMap = keyValuesMap
						.get(imageName);
				if (imageMap != null) {
					try {
						this.dataWriter.writeDataToImage(imageId, "Image data",
								imageMap);
					} catch (final DSOutOfServiceException ex) {
						this.writeToLog("DSAccess error trying to save metadata for image ");
						this.writeToLog(imageName);
						this.writeToLog("\n");
						this.writeToLog(ex.toString());
						this.writeToLog("\n");
					} catch (final DSAccessException ex) {
						this.writeToLog("DSAccess error trying to save metadata for image ");
						this.writeToLog(imageName);
						this.writeToLog("\n");
						this.writeToLog(ex.toString());
						this.writeToLog("\n");
					}
					this.writeToLog("Metadata for image ");
					this.writeToLog(imageName);
					this.writeToLog(" successfully written");
					this.writeToLog("\n");
				} else {
					this.writeToLog("Metadata for image ");
					this.writeToLog(imageName);
					this.writeToLog(" not available");
					this.writeToLog("\n");
				}
				this.copyFile(destinationPath, projectDatasetName, image);
			}
		}
		this.writeToPreviouslyImportedFile(imported);
	}

	// Part 1 or more, counting "Cancer Avatar * metadata" string in the file
	Map<String, String> readMetadataFromFile(final File f, final int part) {
		final Map<String, String> map = new LinkedHashMap<String, String>();
		FileReader fr = null;
		try {
			fr = new FileReader(f);
		} catch (final FileNotFoundException ex) {
			this.writeToLog("File not found error opening file in OmeroImporter for ");
			this.writeToLog(f.getName());
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
			return null;
		}

		final BufferedReader br = new BufferedReader(fr);

		int counter = 0;
		String line = null;
		try {
			line = br.readLine();
			while (line != null) {
				if (line.matches(OmeroImporterFlat.metadataIdent)) {
					counter++;
				}
				if (counter > part) {
					break;
				}
				if (counter < part) {
					line = br.readLine();
					continue;
				}
				final String tokens[] = line.split(",");
				if ((tokens.length < 2)
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
			this.writeToLog("Read line error in OmeroImporter for ");
			this.writeToLog(f.getName());
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
		}

		try {
			br.close();
			fr.close();
		} catch (final IOException ex) {
			this.writeToLog("IO error closing file in OmeroImporter for ");
			this.writeToLog(f.getName());
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
			return null;
		}

		return map;
	}

	public void copyFile(final String destination, final String folderName,
			final File f) {
		final File dir = new File(destination + File.separator + folderName);
		if (!dir.exists()) {
			dir.mkdir();
		}

		final String newFile = dir.getAbsolutePath() + File.separator
				+ f.getName();
		try {
			Files.copy(Paths.get(f.getAbsolutePath()), Paths.get(newFile));
		} catch (final FileAlreadyExistsException ex) {
			this.writeToLog("File already exists in destination, copy avoided : ");
			this.writeToLog(f.getName());
			this.writeToLog("\n");
		} catch (final IOException ex) {
			this.writeToLog("IO error in OmeroImporter during file copy for ");
			this.writeToLog(f.getName());
			this.writeToLog("\n");
			this.writeToLog(ex.toString());
			this.writeToLog("\n");
		}
		if (this.deleteFiles) {
			try {
				Files.delete(Paths.get(f.getAbsolutePath()));
			} catch (final IOException ex) {
				this.writeToLog("IO error in OmeroImporter during file delete for ");
				this.writeToLog(f.getName());
				this.writeToLog("\n");
				this.writeToLog(ex.toString());
				this.writeToLog("\n");
			}
			if (dir.listFiles().length == 0) {
				dir.delete();
			}
		}
	}

	public static void main(final String[] args) {
		String hostName = "localhost", port = "4064", userName = null, password = null;
		String target = System.getProperty("user.dir");
		String destination = System.getProperty("user.dir");
		boolean headless = false;
		boolean configFile = false;
		boolean deleteFiles = false;
		if (args.length == 0) {
			System.out.println("-h for help");
		}
		if ((args.length == 1) && (args[0].equals("-h"))) {
			System.out.println("-cfg, to use config file to pass parameter");
			System.out.println("-H <hostName>, localhost by default");
			System.out.println("-P <port>, 4064 by default");
			System.out.println("-u <userName>");
			System.out.println("-p <password>");
			System.out
					.println("-t <target>, target directory to launch the importer");
			System.out
					.println("-d <destination>, destination directory where to move files after import");
			System.out.println("-hl, to avoid dialogs interaction");
			System.out.println("-dl, to delete files after import and copy");
		}

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
			if (args[i].equals("-dl")) {
				deleteFiles = true;
			}
		}

		final OmeroImporterFlat importer = new OmeroImporterFlat();
		importer.initLogFiles();

		if (configFile) {
			final Map<String, String> parameters = importer.readConfigFile();
			if (parameters == null) {
				importer.writeToLog("Reading parameters from config file failed in OmeroImporter, application terminated");
				System.exit(0);
			}

			for (final String key : parameters.keySet()) {
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
			}
		}
		
		importer.setDeleteFiles(deleteFiles);
		importer.setHeadless(headless);

		if (userName == null) {
			importer.writeToLog("Username not valid error in OmeroImporter");
			return;
		}
		if (password == null) {
			importer.writeToLog("Password not valid error in OmeroImporter");
			return;
		}

		Integer portI = null;
		try {
			portI = Integer.valueOf(port);
		} catch (final Exception ex) {
			// ex.printStackTrace();
		}
		if (portI == null) {
			importer.writeToLog("Target port is not valid error in OmeroImporter");
			return;
		}

		final File f1 = new File(target);
		if (!f1.exists()) {
			importer.writeToLog("Target directory doesn't exists error in OmeroImporter\n");
			return;
		}
		if (!f1.isDirectory()) {
			importer.writeToLog("Target directory is not a directory error in OmeroImporter\n");
			return;
		}

		final File f2 = new File(destination);
		if (!f2.exists()) {
			importer.writeToLog("Destination directory doesn't exists error in OmeroImporter\n");
			return;
		}
		if (!f2.isDirectory()) {
			importer.writeToLog("Destination directory is not a directory error in OmeroImporter\n");
			return;
		}

		importer.setServerAccessInformation(hostName, portI, userName, password);

		try {
			importer.init();
		} catch (final Exception ex) {
			importer.writeToLog("Error during OmeroImporter initialization");
			importer.writeToLog("\n");
			importer.writeToLog(ex.toString());
			importer.writeToLog("\n");
			importer.close();
			return;
		}

		importer.importImages(target, destination);

		importer.close();
	}

	private void setHeadless(final boolean headless) {
		this.headless = headless;
	}

	private void setDeleteFiles(final boolean deleteFiles) {
		this.deleteFiles = deleteFiles;
	}

}
