package com.compomics.sigpep.persistence.dao;

import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * @TODO: JavaDoc missing
 *
 * Created by IntelliJ IDEA.<br/>
 * User: mmueller<br/>
 * Date: 29-May-2008<br/>
 * Time: 17:51:39<br/>
 */
public interface SimpleQueryDao {

    /**
     * Returns the number of protein entries in the database.
     *
     * @return gene count
     */
    public int getProteinCount();

    /**
     * Returns the number of gene entries in the database.
     *
     * @return gene count
     */
    int getGeneCount();

    /**
     * Returns the number of protein sequence entries in the database.
     *
     * @return protein sequence count
     */
    int getSequenceCount();

    /**
     * Returns the number of protease entries in the database.
     *
     * @return protease count
     */
    int getProteaseCount();

    /**
     * Returns the sequence ids and strings.
     *
     * @return the sequence ids and strings
     */
    Map<Integer, String> getSequenceIdsAndStrings();

    /**
     * Returns the peptide sequences by protease short names.
     *
     * @param proteaseShortNames the short names for the proteases
     * @return the peptide sequences
     */
    Set<String> getPeptideSequencesByProteaseShortNames(Set<String> proteaseShortNames);

    /**
     * Returns the signature peptide sequences by short protease names.
     *
     * @param proteaseShortNames the short names for the proteases
     * @return the signature peptide sequences
     */
    Set<String> getSignaturePeptideSequencesByProteaseShortNames(Set<String> proteaseShortNames);

    /**
     * Returns the signature peptide ids by protease short names protein level.
     *
     * @param proteaseShortNames the short names for the proteases
     * @return the signature peptide ids by protease short names protein level
     */
    Set<Integer> getSignaturePeptideIdsByProteaseShortNamesProteinLevel(Set<String> proteaseShortNames);

    /**
     * Returns the signature peptide ids by protease short names gene level.
     *
     * @param proteaseShortNames the short names for the proteases
     * @return the signature peptide ids 
     */
    Set<Integer> getSignaturePeptideIdsByProteaseShortNamesGeneLevel(Set<String> proteaseShortNames);


    /**
     * Returns the species taonomy ids and names.
     *
     * @return the species taonomy ids and names
     */
    Map<Integer, String> getSpeciesTaxonIdsAndNames();

    /**
     * Returns a map of protein accessions to gene accessions.
     *
     * @return a map with protein accessions as key and gene accessions as value
     */
    Map<String, String> getProteinAccessionToGeneAccessionMap();

    /**
     * Returns a map of protein sequence IDs to protein accessions.
     *
     * @return a map of sequence IDS and protein accessions
     */
    Map<Integer, Set<String>> getSequenceIdToProteinAccessionMap();

    /**
     * Returns a map of sequence IDs and peptide feature coordinates for a set of proteases.
     *
     * @param proteaseShortNames a set of protease short names
     * @return map with sequence ID as key and array of coordinates (first element := start coordinate;
     *         second element := end coordinate) as value
     */
    Map<Integer, List<int[]>> getPeptideFeatureCoordinatesByProteaseShortNames(Set<String> proteaseShortNames);

    /**
     * Returns a map of peptide sequences and IDs of the protein sequences they are generated by using the protease set
     * specified.
     *
     * @param peptideSequences   the peptide sequences
     * @param proteaseShortNames the generating proteases
     * @return a map of peptide sequences and sets of protein sequence IDs
     */
    Map<String, Set<Integer>> getSequenceIdsByPeptideSequenceAndProteaseShortName(Set<String> peptideSequences, Set<String> proteaseShortNames);

    /**
     * Returns the sequences of peptides generated by a set of proteases.
     *
     * @param proteaseShortNames the protease shortnames
     * @return the peptide sequences
     */
    Set<String> getPeptideSequencesByProteaseShortName(Set<String> proteaseShortNames);

    /**
     * Returns the sequences of signature peptides generated by a set of proteases.
     *
     * @param proteaseShortNames the protease shortnames
     * @return the peptide sequences
     */
    Set<String> getSignaturePeptideSequencesByProteaseShortName(Set<String> proteaseShortNames);

    /**
     * Returns proteins that are the product of genes alternatively spliced on transcript level.
     *
     * @return a set of protein accessions
     */
    Set<String> fetchAlternativelySplicedProteinsTranscriptLevel();

    /**
     * Returns the primary key of the last protein entry.
     *
     * @return the entry ID
     */
    int getLastProteinId();

    /**
     * Returns the primary key of the last gene entry.
     *
     * @return the entry ID
     */
    int getLastGeneId();

    /**
     * Returns a map of protease IDs and protease shortnames.
     *
     * @return a map with protease IDs as key and protease short names as value
     */
    Map<Integer, String> getProteaseIdToProteaseShortNameMap();

    /**
     * Returns a map of protease names and shortnames to protease IDs.
     *
     * @return a map of protease names and shortnames to protease IDs
     */
    Map<String, Integer> getProteaseNameToProteaseIDMap();

    /**
     * Returns a map of gene IDs and gene accessions.
     *
     * @return a map with gene IDs as key and gene accessions as value
     */
    Map<Integer, String> getGeneIdToGeneAccessionMap();

    /**
     * Returns a map of protein IDs and protein accessions.
     *
     * @return a map with protein IDs as key and protein accessions value
     */
    Map<Integer, String> getProteinIdsToProteinAccessionMap();

    /**
     * Returns the peptide feature ids by peptide id and sequence id.
     *
     * @param peptideId the peptide ids
     * @param sequenceId the sequence ids
     * @return the peptide feature ids
     */
    Set<Integer> getPeptideFeatureIdsByPeptideIdAndSequenceId(Set<Integer> peptideId, Set<Integer> sequenceId);

    /**
     * Returns the accessions alternatively spliced genes transcript level.
     *
     * @return the accessions alternatively spliced genes transcript level
     */
    Set<String> getAccessionsAlternativelySplicedGenesTranscriptLevel();

    /**
     * Returns the accessions alternatively spliced genes transition level.
     *
     * @return the accessions alternatively spliced genes transition level
     */
    Set<String> getAccessionsAlternativelySplicedGenesTranslationLevel();

    /**
     * Return the peptide length frequency by protease short names.
     *
     * @param proteaseShortNames the short names for the proteases
     * @return the peptide length frequency
     */
    Map<Integer, Integer> getPeptideLengthFrequencyByProteaseShortName(Set<String> proteaseShortNames);

    /**
     * Returns the gene accession numbers.
     *
     * @return the gene accession numbers
     */
    Set<String> getGeneAccessions();

    /**
     * Returns the protein sequence strings.
     *
     * @return the protein sequence strings
     */
    Set<String> getProteinSequenceStrings();

    /**
     * Returns the protein accession numbers.
     * 
     * @return the protein accession numbers
     */
    Set<String> getProteinAccessions();
}
