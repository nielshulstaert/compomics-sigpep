package com.compomics.sigpep.analysis;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import com.compomics.dbtools.DatabaseException;
import com.compomics.sigpep.model.*;
import com.compomics.sigpep.model.impl.ProteinSequenceImpl;
import com.compomics.sigpep.persistence.rdbms.SigPepDatabase;
import com.compomics.sigpep.persistence.util.HibernateUtil;
import com.compomics.sigpep.model.Persistable;
import com.compomics.sigpep.util.DelimitedTableReader;
import com.compomics.sigpep.util.DelimitedTableWriter;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Provides methods to analyse the splice event coverage of sets of peptides.
 * <p/>
 * Created by IntelliJ IDEA.<br/>
 * User: mmueller<br/>
 * Date: 08-Feb-2008<br/>
 * Time: 14:53:39<br/>
 */
public class SpliceEventAnalyser {

    /**
     * the SigPep database
     */
    private SigPepDatabase sigPepDatabase;
    /**
     * the log4j logger
     */
    private static Logger logger = Logger.getLogger(SpliceEventAnalyser.class);
    /**
     * the delimited used to separate columns in the output file
     */
    private String tableColumnDelimiter = "\t";
    /**
     * protein sequence to protein accession map
     */
    private Map<Integer, Set<String>> sequenceId2ProteinAccession;
    /**
     * protein accession to gene accession map
     */
    private Map<String, String> proteinAccession2GeneAccession;
    /**
     * gene accession to protein count map
     */
    private Map<String, Integer> geneAccession2ProteinCount;
    /**
     * gene accession to sequence ID map
     */
    private Map<String, Set<Integer>> geneAccession2SequenceId;
    /**
     * the Hibernate session factory
     */
    private SessionFactory sessionFactory;
    /**
     * the SigPepDB JDBC connection
     */
    private Connection connection;
    /**
     * true if maps have been fetched from the database
     */
    private boolean mapsFetched = false;

    /**
     * @param ncbiTaxonId the NCBI taxon ID identifying the SigPep species
     * @throws SQLException if an exception occurs while connecting to the database
     */
    public SpliceEventAnalyser(int ncbiTaxonId) throws SQLException {
        //this.sigPepDatabase = sigPepDatabase;
        this.sessionFactory = HibernateUtil.getSessionFactory(ncbiTaxonId);
        this.connection = sigPepDatabase.getConnection();
    }

    /**
     * Creates a report for peptides generated by a set of proteaseFilter that span splice events.
     *
     * @param outputStream  were the report goes
     * @param proteaseNames the short names of the peptide generating protease(s)
     * @throws SQLException if an exception occurs while connecting to the SigPep database
     */
    public void reportSpliceEventCoverage(OutputStream outputStream,
                                          Set<String> proteaseNames) throws SQLException {

        logger.info("fetching splice site spanning peptides for protease(s) " + proteaseNames.toString() + "...");
        int[] peptideIds = fetchSpliceEventSpanningPeptides(proteaseNames, null);

        reportSpliceEventCoverageForPeptides(outputStream, peptideIds, proteaseNames);
    }

    /**
     * Creates a report for a subset of peptides generated by a set of proteaseFilter that span splice events.
     *
     * @param outputStream  were the report goes
     * @param peptideIds    the peptide IDs identifying the peptide subset
     * @param proteaseNames the short names of the peptide generating protease(s)
     * @throws SQLException if an exception occurs while connecting to the SigPep database
     */
    public void reportSpliceEventCoverage(OutputStream outputStream,
                                          Set<Integer> peptideIds,
                                          Set<String> proteaseNames) throws SQLException {

        logger.info("fetching splice site spanning peptides for protease(s) " + proteaseNames.toString() + "...");
        int[] spliceEventSpanningPeptideIds = fetchSpliceEventSpanningPeptides(proteaseNames, peptideIds);

        reportSpliceEventCoverageForPeptides(outputStream, spliceEventSpanningPeptideIds, proteaseNames);
    }

    /**
     * Creates a report of splice event coverage for a set of peptide features.
     *
     * @param outputStream  were the report goes
     * @param peptideIds    the IDs of the peptides to analyse
     * @param proteaseNames the short names of the proteaseFilter generating the peptides
     * @throws SQLException if an exception occurs while connecting to the SigPep database
     */
    private void reportSpliceEventCoverageForPeptides(OutputStream outputStream,
                                                      int[] peptideIds,
                                                      Set<String> proteaseNames)
            throws SQLException {

        if (!mapsFetched) {
            fetchMaps();
        }

        logger.info("analysing splice event coverage...");
        //write output
        DelimitedTableWriter delimitedTableWriter = new DelimitedTableWriter(outputStream, tableColumnDelimiter, false);

        //table header
        delimitedTableWriter.writeHeader(
                "peptide_id",
                "splice_event_count",
                "splice_event_specific",
                "isoform_specific_splice_event",
                "transcript_splice_event",
                "translation_splice_events",
                "translation_splice_event_count",
                "sequence_splice_events",
                "sequence_splice_event_count",
                "gene",
                "gene_count",
                "gene_alt_splice",
                "gene_translation_count",
                "gene_sequence_count");

        int count = 0;
        int increment = 1000;
        for (int from = 0, to = 0; from < peptideIds.length; from = from + increment) {

            to = from + increment;

            if (to >= peptideIds.length) {
                to = peptideIds.length - 1;
            }

            //convert array to set
            Set<Integer> peptideIdSubset = new HashSet<Integer>();
            for (int id : Arrays.copyOfRange(peptideIds, from, to)) {
                peptideIdSubset.add(id);
            }

            Session session = sessionFactory.openSession(connection);
            for (Integer peptideId : peptideIdSubset) {
                analyseSpliceEventCoverage(peptideId, proteaseNames, session, delimitedTableWriter);
            }
            session.close();

            count = to;

            if (count % 1000 == 0) {
                logger.info(count + " of " + peptideIds.length + " peptides processed ...");
            }
        }
    }

    /**
     * Fetches maps required for the analysis.
     */
    private void fetchMaps() {

        logger.info("fetching sequence-id-to-protein-accession map...");
        sequenceId2ProteinAccession = fetchSequenceId2ProteinAccessionMap();

        logger.info("fetching protein-accession-to-gene-accession map...");
        proteinAccession2GeneAccession = fetchProteinAccession2GeneAccession();

        logger.info("fetching gene-accession-to-protein-count map...");
        geneAccession2ProteinCount = fetchGeneAccession2ProteinCountMap();

        logger.info("fetching gene-accession-to-sequence-id map...");
        geneAccession2SequenceId = fetchGeneAccession2SequenceId();
    }

    /**
     * Analyses the splice event coverage for a set of peptides
     * and appends the results to a delimited table.
     *
     * @param peptideId            the IDs of the peptide features to do the analysis for
     * @param proteaseNames        the set of protease generating the peptides
     * @param session              a Hibernate session
     * @param delimitedTableWriter the delimited table writer
     */
    private void analyseSpliceEventCoverage(Integer peptideId,
                                            Set<String> proteaseNames,
                                            Session session,
                                            DelimitedTableWriter delimitedTableWriter) {

        List<PeptideFeature> peptideFeatures = fetchPeptideFeatures(peptideId, proteaseNames, session);
        Set<SequenceLocation> peptideLocations = new HashSet<SequenceLocation>();
        Set<SpliceEvent> spliceEvents = new HashSet<SpliceEvent>();
        Set<SequenceLocation> spliceEventLocations = new HashSet<SequenceLocation>();

        for (PeptideFeature peptideFeature : peptideFeatures) {

            //get proteome locations of peptide
            peptideLocations.add(peptideFeature.getLocation());

            for (SpliceEventFeature spliceEventFeature : peptideFeature.getSpliceEventFeatures()) {

                //get transcript splice event
                spliceEvents.add(spliceEventFeature.getFeatureObject());

                //get proteome locations of transcript splice event the peptide spans
                spliceEventLocations.add(spliceEventFeature.getLocation());
            }
        }

        int spliceEventCount = spliceEvents.size();
        int spliceEventSpecific = 0;
        int isoformSpecificSpliceEvent = -1;
        String transcriptSpliceEvent = "-1";
        String translationSpliceEventsString = "-1";
        int translationSpliceEventCount = -1;
        String sequenceSpliceEventsString = "-1";
        int sequenceSpliceEventCount = -1;
        int geneCount = -1;
        String genesString = "-1";
        int geneAltSplice = -1;
        int proteinCount = -1;
        int sequenceCount = -1;

        //filter for peptides mapping to exactly one transcript splice event
        if (spliceEventCount == 1) {

            //check if the peptide is specific to the splice event location...
            Set<String> sequenceSpliceEvents = new HashSet<String>();
            Set<SequenceLocation> spliceEventSpecificLocations = new HashSet<SequenceLocation>();

            //...get splice event (there is only one because of the above filter)...
            SpliceEvent event = (SpliceEvent) spliceEvents.toArray()[0];

            //...get event locations on protein level...
            for (SequenceLocation eventLocation : spliceEventLocations) {

                //...get the peptide locations that overlap with the event location
                //on protein sequence level...
                for (SequenceLocation peptideLocation : peptideLocations) {

                    if (eventLocation.getSequence().equals(peptideLocation.getSequence())
                            && peptideLocation.getStart() < eventLocation.getStart()
                            && peptideLocation.getEnd() > eventLocation.getEnd()) {

                        int sequenceId = ((ProteinSequenceImpl) eventLocation.getSequence()).getId();
                        int eventId = ((Persistable) event).getId();

                        //...save splice event specific locations and
                        //the splice event on protein sequence level
                        spliceEventSpecificLocations.add(peptideLocation);
                        sequenceSpliceEvents.add(sequenceId + "-" + eventLocation.getStart());

                    }

                }

            }

            //...check if peptide is splice event specific
            //if all peptide locations span the splice event location
            //the peptide location is splice event specific
            if (spliceEventSpecificLocations.size() == peptideLocations.size()) {
                spliceEventSpecific = 1;
            } else {
                spliceEventSpecific = 0;
            }

            //...check if splice event is isoform-specific,
            // i.e. only occurs in one protein sequence...
            if (sequenceSpliceEvents.size() == 1) {
                isoformSpecificSpliceEvent = 1;
                //...if it occurs in in more then one protein sequence
                //it's non-isoform specific event
            } else {
                isoformSpecificSpliceEvent = 0;
            }

            //convert sequence splice events to translation splice events
            //and get respective genes
            Set<String> translationSpliceEvents = new HashSet<String>();
            Set<String> genes = new HashSet<String>();
            for (String sequenceSpliceEvent : sequenceSpliceEvents) {

                int sequenceId = new Integer(sequenceSpliceEvent.split("-")[0]);
                int sequenceLocation = new Integer(sequenceSpliceEvent.split("-")[1]);

                Set<String> proteinAccessions = sequenceId2ProteinAccession.get(sequenceId);

                for (String proteinAccession : proteinAccessions) {

                    translationSpliceEvents.add(new StringBuffer().append(proteinAccession).append(":").append(sequenceLocation).toString());

                    String geneAccession = proteinAccession2GeneAccession.get(proteinAccession);
                    proteinCount = geneAccession2ProteinCount.get(geneAccession);
                    sequenceCount = geneAccession2SequenceId.get(geneAccession).size();
                    genes.add(geneAccession);


                }

            }

            transcriptSpliceEvent = event.getUpstreamExon().getPrimaryDbXref().getAccession() + ":" + event.getDownstreamExon().getPrimaryDbXref().getAccession();
            sequenceSpliceEventsString = sequenceSpliceEvents.toString().replace("[", "").replace("]", "");
            sequenceSpliceEventCount = sequenceSpliceEvents.size();
            translationSpliceEventsString = translationSpliceEvents.toString().replace("[", "").replace("]", "");
            translationSpliceEventCount = translationSpliceEvents.size();
            genesString = genes.toString().replace("[", "").replace("]", "");
            geneCount = genes.size();

            if (sequenceCount > 1) {
                geneAltSplice = 1;
            } else {
                geneAltSplice = 0;
            }

            if (geneCount > 1) {
                proteinCount = -1;
                sequenceCount = -1;
                geneAltSplice = -1;
            }

            delimitedTableWriter.writeRow(
                    peptideId, //peptide_id
                    spliceEventCount, //splice_event_count
                    spliceEventSpecific, //splice_event_specific
                    isoformSpecificSpliceEvent, //isoform_specific_splice_event
                    transcriptSpliceEvent, //transcript_splice_event
                    translationSpliceEventsString, //translation_splice_events
                    translationSpliceEventCount, //translation_splice_event_count
                    sequenceSpliceEventsString, //sequence_splice_events
                    sequenceSpliceEventCount, //sequence_splice_event_count
                    genesString, //gene
                    geneCount, //gene_count
                    geneAltSplice, //gene_alt_splice
                    proteinCount, //gene_translation_count
                    sequenceCount);                  //gene_sequence_count
        }
    }

    /**
     * Returns the sequence ID -> protein accession mapping.
     *
     * @return a map of sequence IDs and protein accession sets
     */
    private Map<Integer, Set<String>> fetchSequenceId2ProteinAccessionMap() {

        Session session = sessionFactory.openSession(connection);

        Map<Integer, Set<String>> sequenceId2ProteinAccession = new HashMap<Integer, Set<String>>();

        Query query = session.createQuery(
                "select protein.sequence.id, protein.primaryDbXref.accession from Protein protein");

        for (Iterator<Object[]> result = query.iterate(); result.hasNext(); ) {

            Object[] row = result.next();
            Integer sequenceId = (Integer) row[0];
            String proteinAccession = (String) row[1];

            if (!sequenceId2ProteinAccession.containsKey(sequenceId)) {
                sequenceId2ProteinAccession.put(sequenceId, new HashSet<String>());
            }
            sequenceId2ProteinAccession.get(sequenceId).add(proteinAccession);
        }

        session.close();

        return sequenceId2ProteinAccession;
    }

    /**
     * Returns the protein accession -> gene accession mapping.
     *
     * @return a map of protein accessions and gene accessions
     */
    private Map<String, String> fetchProteinAccession2GeneAccession() {

        Session session = sessionFactory.openSession(connection);

        Map<String, String> proteinAccession2GeneAccession = new HashMap<String, String>();

        Query query = session.createQuery(
                "select protein.primaryDbXref.accession, gene.primaryDbXref.accession  from Gene gene inner join gene.proteins protein");

        for (Iterator<Object[]> result = query.iterate(); result.hasNext(); ) {
            Object[] row = result.next();
            String proteinAccession = (String) row[0];
            String geneAccession = (String) row[1];
            proteinAccession2GeneAccession.put(proteinAccession, geneAccession);
        }

        session.close();

        return proteinAccession2GeneAccession;
    }

    /**
     * Returns the protein count of genes.
     *
     * @return a map of gene accessions and protein counts
     */
    private Map<String, Integer> fetchGeneAccession2ProteinCountMap() {

        Session session = sessionFactory.openSession(connection);

        Map<String, Integer> geneAccession2ProteinCount = new HashMap<String, Integer>();
        Query query = session.createQuery(
                "select gene.primaryDbXref.accession, size(proteins) from Gene gene group by gene");

        for (Iterator result = query.iterate(); result.hasNext(); ) {
            Object[] object = (Object[]) result.next();
            String geneAccession = (String) object[0];
            int proteinCount = (Integer) object[1];
            geneAccession2ProteinCount.put(geneAccession, proteinCount);
        }

        session.close();

        return geneAccession2ProteinCount;
    }

    /**
     * Returns the gene accession -> sequence ID mapping.
     *
     * @return a map of gene accessions and sets of sequence IDs
     */
    private Map<String, Set<Integer>> fetchGeneAccession2SequenceId() {

        Session session = sessionFactory.openSession(connection);

        Map<String, Set<Integer>> geneAccession2SequenceId = new HashMap<String, Set<Integer>>();

        Query query = session.createQuery(
                "select gene.primaryDbXref.accession as accession, protein.sequence.id "
                        + "from Gene gene "
                        + "inner join gene.proteins protein");

        for (Iterator<Object[]> result = query.iterate(); result.hasNext(); ) {
            Object[] row = result.next();
            String geneAccession = (String) row[0];
            Integer sequenceId = (Integer) row[1];
            if (!geneAccession2SequenceId.containsKey(geneAccession)) {
                geneAccession2SequenceId.put(geneAccession, new HashSet<Integer>());
            }

            geneAccession2SequenceId.get(geneAccession).add(sequenceId);
        }

        session.close();

        return geneAccession2SequenceId;
    }

    /**
     * Fetches peptides generated by a specified set of proteaseFilter
     * that span splice events.
     *
     * @param proteaseNames the short names of the proteaseFilter
     * @param peptideIds    a list of peptide ids to filter by. If null or empty peptide features for all
     *                      peptides generated by the proteaseFilter will be returned
     * @return an array of peptide IDs
     */
    private int[] fetchSpliceEventSpanningPeptides(Set<String> proteaseNames,
                                                   Set<Integer> peptideIds) {

        Session session = sessionFactory.openSession(connection);

        Query query;
        if (peptideIds == null || peptideIds.isEmpty()) {

            query = session.createQuery(
                    "select peptideFeature.id from PeptideFeature peptideFeature "
                            + "inner join peptideFeature.proteaseFilter protease "
                            + "where protease.name in (:proteaseNames) "
                            + "and peptideFeature.spliceEventFeatures.size > 0");
            query.setParameterList("proteaseNames", proteaseNames);

        } else {

            query = session.createQuery(
                    "select peptideFeature.id from PeptideFeature peptideFeature "
                            + "inner join peptideFeature.proteaseFilter protease "
                            + "where protease.name in (:proteaseNames) "
                            + "and peptideFeature.featurObject.id IN (:peptideIds) "
                            + "and peptideFeature.spliceEventFeatures.size > 0");

            query.setParameterList("proteaseNames", proteaseNames);
            query.setParameterList("peptideIds", peptideIds);

        }

        Object[] result = query.list().toArray();
        int[] retVal = new int[result.length];
        for (int i = 0; i < result.length; i++) {
            retVal[i] = (Integer) result[i];
        }

        session.close();

        return retVal;
    }

    /**
     * Fetches PeptideFeatures for a set of peptide IDs.
     *
     * @param peptideId     the peptide IDs
     * @param proteaseNames the names of the proteaseFilter generating the peptides
     * @param session       the SigPepDB Hibernate session
     * @return a list of PeptideFeatures
     */
    private List<PeptideFeature> fetchPeptideFeatures(Integer peptideId,
                                                      Set<String> proteaseNames,
                                                      Session session) {

        Query query = session.createQuery(
                "select peptideFeature from PeptideFeature peptideFeature inner join peptideFeature.proteaseFilter protease "
                        + "where peptideFeature.featureObject.id = :peptideId "
                        + "and protease.name in (:proteaseNames)");
        query.setParameter("peptideId", peptideId);
        query.setParameterList("proteaseNames", proteaseNames);

        return query.list();
    }

    /**
     * @param inputFileName
     * @return
     * @throws FileNotFoundException
     * @TODO: JavaDoc missing.
     */
    private static Set<Integer> readPeptideIds(String inputFileName) throws FileNotFoundException {

        DelimitedTableReader tableReader = new DelimitedTableReader(new FileInputStream(inputFileName), "\t");


        Set<Integer> retVal = new HashSet<Integer>();
        for (Iterator<String[]> rows = tableReader.read(); rows.hasNext(); ) {

            String[] row = rows.next();

            try {
                int peptideId = new Integer(row[0]);
                retVal.add(peptideId);

            } catch (NumberFormatException e) {
                logger.warn("Exception while parsing input string " + Arrays.toString(row) + ". Skipped.", e);
            }
        }

        return retVal;
    }

    /**
     * @param args
     * @TODO: JavaDoc missing.
     */
    public static void main(String[] args) {

        //--user=<MySQL username> --password=<password> --taxon=9606 --protease=tryp --out=/home/mmueller/sigpep_splice_event_coverage_tryp_9606_new.tsv

        String usage = "SpliceEventAnalyser \n"
                + "--user=SIGPEPDB_USERNAME \n"
                + "--password=SIGPEPDB_PASSWORD \n"
                + "--taxon=NCBI_TAXON_ID \n"
                + "--protease=PROTEASE_SHORT_NAME [,PROTEASE_SHORT_NAME,...]\n"
                + "[--peptides=PEPTIDE_ID_INPUT_FILENAME]\n"
                + "--out=PATH_TO_OUTPUT_FILE";

        Map<String, String> commandLineArgs = parseCommandLineArguments(args);

        //check for presence of all required command line arguments
        if (!(commandLineArgs.containsKey("user")
                && commandLineArgs.containsKey("password")
                && commandLineArgs.containsKey("taxon")
                && commandLineArgs.containsKey("protease")
                && commandLineArgs.containsKey("out"))) {

            System.out.println(usage);
            System.exit(1);
        }

        String username = commandLineArgs.get("user");
        String password = commandLineArgs.get("password");
        int taxonId = new Integer(commandLineArgs.get("taxon"));
        String outputFileName = commandLineArgs.get("out");
        String proteaseNames = commandLineArgs.get("protease");
        String inputFileName = null;
        if (commandLineArgs.containsKey("peptides")) {
            inputFileName = commandLineArgs.get("peptides");
        }

        try {

            SigPepDatabase sigPepDb = new SigPepDatabase(username, password.toCharArray(), taxonId);
            SpliceEventAnalyser sec = new SpliceEventAnalyser(taxonId);
            OutputStream outputStream = new FileOutputStream(outputFileName);

            //parse protease names
            if (proteaseNames != null && inputFileName == null) {

                logger.info("Analysing peptide coverage of " + proteaseNames + " peptides...");
                Set<String> proteases = new HashSet<String>(Arrays.asList(proteaseNames.split(",")));
                sec.reportSpliceEventCoverage(outputStream, proteases);
                outputStream.close();
                logger.info("done");

            } else if (proteaseNames != null && inputFileName != null) {

                logger.info("Analysing peptide coverage of peptides in file " + inputFileName + " generated by protease(s) " + proteaseNames + "...");
                Set<String> proteases = new HashSet<String>(Arrays.asList(proteaseNames.split(",")));
                Set<Integer> peptideIds = readPeptideIds(inputFileName);
                sec.reportSpliceEventCoverage(outputStream, peptideIds, proteases);
                logger.info("done");

            } else {
                logger.error(usage);
            }
        } catch (IOException e) {
            logger.error(e);
            System.exit(1);
        } catch (DatabaseException e) {
            logger.error(e);
            System.exit(1);
        } catch (SQLException e) {
            logger.error(e);
            System.exit(1);
        }
    }

    /**
     * @param args
     * @return
     * @TODO: JavaDoc missing.
     */
    public static Map<String, String> parseCommandLineArguments(String[] args) {

        //parse command line arguments
        Map<String, String> retVal = new HashMap<String, String>();

        for (String arg : args) {
            String key = arg.split("=")[0].replace("--", "");
            String value = arg.split("=")[1];

            retVal.put(key, value);
        }

        return retVal;
    }
}
