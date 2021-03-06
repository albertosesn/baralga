package org.remast.baralga.model.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.remast.baralga.gui.model.PresentationModel;
import org.remast.baralga.gui.settings.ApplicationSettings;
import org.remast.baralga.gui.settings.UserSettings;
import org.remast.baralga.model.ProjectActivity;
import org.remast.baralga.model.export.Exporter;
import org.remast.baralga.model.export.XmlExporter;

/**
 * Misc utility methods for creating and reading backups.
 * @author remast
 */
public class DataBackup {

	/** The logger. */
	private static final Logger log = LoggerFactory.getLogger(DataBackup.class);

	/** The date format for dates used in the names of backup files. */
	private static final SimpleDateFormat BACKUP_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

	/** The name of the backed up corrupt data file. */
	private static final String ERROR_FILE_NAME = UserSettings.DEFAULT_FILE_NAME + ".Error";

	/** The number of backup files to keep. */
	private static final int NUMBER_OF_BACKUPS = 3;

	/**
	 * Create a backup from given file.
	 * @param model 
	 * @param toBackup the file to be backed up
	 */
	public static void createBackup(final PresentationModel model) {
		OutputStream outputStream = null;
		final File backupFile = new File(UserSettings.instance().getDataFileLocation() + "." + BACKUP_DATE_FORMAT.format(new Date()));
		try {
			outputStream = new FileOutputStream(backupFile);

			final Exporter exporter = new XmlExporter();

			// Get activities for export
			Collection<ProjectActivity> activitiesForExport = null;
			if (exporter.isFullExport()) {
				activitiesForExport = model.getAllActivitiesList();
			} else {
				activitiesForExport = model.getActivitiesList();
			}

			synchronized (activitiesForExport) {
				exporter.export(
						activitiesForExport,
						model.getFilter(),
						outputStream
						);
			}

			// Make sure everything is written.
			outputStream.flush();
		} catch (Throwable t) {
			log.error(t.getLocalizedMessage(), t);
		} finally {
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (Throwable t) {
					// Ignore
				}
			}

			cleanupBackupFiles();
		}
	}

	/**
	 * Cleans up old backup files so that not more backup files than <code>NUMBER_OF_BACKUPS</code> exist.
	 */
	private static void cleanupBackupFiles() {
		final List<File> backupFiles = getBackupFiles();
		if (backupFiles != null && backupFiles.size() > NUMBER_OF_BACKUPS) {
			final int numberOfFilesToDelete = backupFiles.size() - NUMBER_OF_BACKUPS;

			for (int i = 1; i <= numberOfFilesToDelete; i++) {
				final File toDelete = backupFiles.get(backupFiles.size() - i);
				final boolean successfull = toDelete.delete();
				if (!successfull) {
					log.error("Could not delete file " + toDelete.getAbsolutePath() + ".");
				}
			}
		}
	}

	/**
	 * Get a list of all backup files in order of the backup date (with the latest backup as first). If 
	 * there there are no backups <code>Collections.EMPTY_LIST</code> is returned.
	 * @return the list of backup files
	 */
	public static List<File> getBackupFiles()  {
		final SortedMap<Date, File> sortedBackupFiles = new TreeMap<Date, File>();

		final File dir = ApplicationSettings.instance().getApplicationDataDirectory();
		final String [] backupFiles = dir.list(new FilenameFilter() {

			public boolean accept(final File dir, final String name) {
				if (!StringUtils.equals(ERROR_FILE_NAME, name) 
						&& !StringUtils.equals(UserSettings.DEFAULT_FILE_NAME, name) 
						&& name.startsWith(UserSettings.DEFAULT_FILE_NAME)) {
					return true;
				}

				return false;
			}

		});

		if (backupFiles == null) {
			return Collections.emptyList();
		}

		for (String backupFile : backupFiles) {
			try {
				final Date backupDate = BACKUP_DATE_FORMAT.parse(backupFile.substring(UserSettings.DEFAULT_FILE_NAME.length() + 1));
				sortedBackupFiles.put(backupDate, new File(ApplicationSettings.instance().getApplicationDataDirectory() + File.separator + backupFile));
			} catch (ParseException e) {
				log.error(e.getLocalizedMessage(), e);
			}
		}

		// Order the list by the date of the backup with the latest backup at front.
		final List<File> backupFileList = new ArrayList<File>(sortedBackupFiles.size());
		final int numberOfBackups = sortedBackupFiles.size();
		for (int i = 0; i < numberOfBackups; i++) {
			final Date backupDate = sortedBackupFiles.lastKey();

			backupFileList.add(sortedBackupFiles.get(backupDate));
			sortedBackupFiles.remove(backupDate);
		}

		return backupFileList;
	}

	/**
	 * Get the date on which the backup file has been created. 
	 * @param backupFile the backup file to get date for
	 * @return The date on which the backup file has been created. If no date could be inferred <code>null</code> is returned.
	 */
	public static Date getDateOfBackup(final File backupFile) {
		try {
			return BACKUP_DATE_FORMAT.parse(backupFile.getName().substring(UserSettings.DEFAULT_FILE_NAME.length() + 1));
		} catch (Exception e) {
			log.error(e.getLocalizedMessage(), e);
		}
		
		return null;
	}

}
