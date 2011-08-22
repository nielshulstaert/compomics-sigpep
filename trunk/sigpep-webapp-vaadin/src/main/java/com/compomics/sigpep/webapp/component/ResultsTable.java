package com.compomics.sigpep.webapp.component;

import com.compomics.acromics.rcaller.RFilter;
import com.compomics.acromics.rcaller.RSource;
import com.compomics.sigpep.webapp.interfaces.Pushable;
import com.compomics.sigpep.webapp.listener.RCallerClickListener;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.vaadin.Application;
import com.vaadin.terminal.ClassResource;
import com.vaadin.ui.*;
import com.vaadin.ui.themes.BaseTheme;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

/**
 * This class represents a series of .tsv transition files as a Table.
 */
public class ResultsTable extends VerticalLayout {

    private static Logger logger = Logger.getLogger(ResultsTable.class);

    /**
     * The table component that shows of the files passed by the constructor.
     */
    Table iTable = new Table();

    public String COLUMN_LABEL_FILENAME = "filename";
    public String COLUMN_LABEL_GRAPH = "graph";
    public String COLUMN_LABEL_FILE = "download";

    /**
     * Temporary folder to generate the images.
     */
    File iTempFolder = Files.createTempDir();

    /**
     * Object to push async events.
     */
    private final Pushable iPushable;

    /**
     * The application in which the Table is running.
     */
    private final Application iApplication;

    /**
     * Create a ResultsTable from a set of files.
     *
     * @param aFiles
     */
    public ResultsTable(ArrayList<File> aFiles, Pushable aPushable, Application aApplication) {
        super();
        setCaption("Results table");
        iPushable = aPushable;
        iApplication = aApplication;
        try {
            // Create a tmp folder for this Table.
            iTempFolder = Files.createTempDir();

            // Initiate the table.
            createTableColumns();
            doFormatting();

            // Fill the table.
            populateTable(aFiles);
            this.addComponent(iTable);

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }



    /**
     * Wrapper method which populates the table with an ArrayList of files.
     *
     * @param aFiles
     */
    private void populateTable(ArrayList<File> aFiles) throws IOException {
        // Iterate all given files.
        for (File lFile : aFiles) {
            // Add a new item to the table.
            Object id = iTable.addItem();

            // 1 - Filename.
            iTable.getContainerProperty(id, COLUMN_LABEL_FILENAME).setValue(generateFileName(lFile));

            // 2- Download link to tsv file.
            Link l = ComponentFactory.createFileDownloadLink(lFile);
            iTable.getContainerProperty(id, COLUMN_LABEL_FILE).setValue(l);

            // 3- Make an R button
            Button lButton = generateBackgroundSignatureButton(lFile);
            iTable.getContainerProperty(id, COLUMN_LABEL_GRAPH).setValue(lButton);
        }
    }

    /**
     * This method creates a Button that will launch the barplot.rj sript
     *
     * @param lFile
     * @return
     */
    private Button generateBackgroundSignatureButton(File lFile) {
        // Create a new button, display as a link.
        Button lButton = new Button();
        lButton.setStyleName(BaseTheme.BUTTON_LINK);

        // Set the image icon from the classpath.
        lButton.setIcon(new ClassResource("/images/graph_sig_bg.png", iApplication));

        // Load the R-script.
        URL aResource = Resources.getResource("r/barplot.rj");
        File lRFile = new File(aResource.getPath());
        RSource lRSource = new RSource(lRFile);
        RFilter lRFilter = new RFilter();
        lRFilter.add("file.input", lFile.getPath());
        File lTempFile = new File(iTempFolder, System.currentTimeMillis() + ".png");
        lRFilter.add("file.output", lTempFile.getPath());

        // Add a listener.
        RCallerClickListener lRCallerClickListener = new RCallerClickListener(lRSource, lRFilter, iPushable, iApplication);
        lButton.addListener(lRCallerClickListener);
        return lButton;
    }



    /**
     * This method creates an apropriate filename to display in the table.
     *
     * @param lFile
     * @return
     */
    private String generateFileName(File lFile) {
        // Remove the extension of the filename
        return lFile.getName().substring(0, lFile.getName().indexOf("."));
    }

    /**
     * Create the
     */
    private void createTableColumns() {
        // Define the Table
        iTable.addContainerProperty(COLUMN_LABEL_FILENAME, Label.class, null);
        iTable.addContainerProperty(COLUMN_LABEL_FILE, Link.class, null);
        iTable.addContainerProperty(COLUMN_LABEL_GRAPH, Button.class, null);

    }

    /**
     * Table formatting
     */
    private void doFormatting() {
        iTable.setWidth("80%");
        iTable.setPageLength(10);
    }
}
