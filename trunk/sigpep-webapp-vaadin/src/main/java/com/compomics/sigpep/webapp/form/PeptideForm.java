package com.compomics.sigpep.webapp.form;

import com.compomics.sigpep.PeptideGenerator;
import com.compomics.sigpep.SigPepQueryService;
import com.compomics.sigpep.SigPepSession;
import com.compomics.sigpep.analysis.SignatureTransitionFinder;
import com.compomics.sigpep.model.Peptide;
import com.compomics.sigpep.model.ProductIonType;
import com.compomics.sigpep.model.Protease;
import com.compomics.sigpep.model.SignatureTransition;
import com.compomics.sigpep.report.SignatureTransitionMassMatrix;
import com.compomics.sigpep.webapp.MyVaadinApplication;
import com.compomics.sigpep.webapp.bean.PeptideFormBean;
import com.compomics.sigpep.webapp.component.ComponentFactory;
import com.compomics.sigpep.webapp.component.ResultsTable;
import com.compomics.sigpep.webapp.factory.PeptideFormFieldFactory;
import com.google.common.io.Files;
import com.vaadin.data.Validator;
import com.vaadin.data.util.BeanItem;
import com.vaadin.data.util.NestedMethodProperty;
import com.vaadin.ui.Button;
import com.vaadin.ui.Form;
import com.vaadin.ui.HorizontalLayout;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: niels
 * Date: 20/09/11
 * Time: 9:43
 * To change this template use File | Settings | File Templates.
 */
public class PeptideForm extends Form {
    private static Logger logger = Logger.getLogger(PeptideForm.class);

    private MyVaadinApplication iApplication;

    private PeptideFormBean iPeptideFormBean;
    private Peptide iPeptide;

    private Vector<String> iOrder;

    private HorizontalLayout iFormButtonLayout;
    private HorizontalLayout iProgressIndicatorLayout;
    private Button iSubmitButton;
    private Button iCancelButton;

    public PeptideForm(String aCaption, PeptideFormBean aPeptideFormBean, Peptide aPeptide, MyVaadinApplication aApplication) {
        this.setCaption(aCaption);
        iApplication = aApplication;

        this.setFormFieldFactory(new PeptideFormFieldFactory());

        iPeptideFormBean = aPeptideFormBean;
        iPeptide = aPeptide;
        BeanItem<PeptideFormBean> lBeanItem = new BeanItem<PeptideFormBean>(iPeptideFormBean);
        lBeanItem.addItemProperty("scientificName", new NestedMethodProperty(lBeanItem.getBean(), "species.scientificName"));
        this.setItemDataSource(lBeanItem);

        iSubmitButton = new Button("Submit", new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent aClickEvent) {
                try {
                    commit();
                    resetValidation();

                    PeptideFormThread lSigPepFormThread = new PeptideFormThread();
                    lSigPepFormThread.start();

                    //add label and progress indicator
                    iProgressIndicatorLayout = ComponentFactory.createProgressIndicator("Processing...");

                    iFormButtonLayout.addComponent(iProgressIndicatorLayout);
                    iFormButtonLayout.requestRepaint();

                    //disable form buttons during run
                    iSubmitButton.setEnabled(Boolean.FALSE);
                    iCancelButton.setEnabled(Boolean.FALSE);

                } catch (Validator.InvalidValueException e) {
                    // Failed to commit. The validation errors are
                    // automatically shown to the user.
                }
            }
        });

        iCancelButton = new Button("Reset", new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent aClickEvent) {
                iApplication.getFormTabSheet().cancelPeptideForm();
            }
        });

        iFormButtonLayout = new HorizontalLayout();
        iFormButtonLayout.setSpacing(Boolean.TRUE);
        iFormButtonLayout.addComponent(iSubmitButton);
        iFormButtonLayout.addComponent(iCancelButton);
        this.getFooter().addComponent(iFormButtonLayout);

        iOrder = new Vector();
        iOrder.add("scientificName");
        iOrder.add("proteaseName");
        iOrder.add("peptideSequence");
        iOrder.add("massAccuracy");
        iOrder.add("minimumCombinationSize");
        iOrder.add("maximumCombinationSize");
        iOrder.add("signatureTransitionFinderType");
        this.setOrder();

        this.setImmediate(Boolean.TRUE);

    }

    private void resetValidation() {
        this.setComponentError(null);
        this.setValidationVisible(false);
        iApplication.clearResultTableComponent();
        this.setOrder();
    }

    private void setOrder() {
        this.setVisibleItemProperties(iOrder);
    }

    private class PeptideFormThread extends Thread {

        public void run() {

            SigPepSession lSigPepSession = MyVaadinApplication.getSigPepSession();

            File outputFolder = Files.createTempDir();
            logger.info(outputFolder);

            SigPepQueryService lSigPepQueryService = MyVaadinApplication.getSigPepSession().createSigPepQueryService();

            Protease aProtease = lSigPepQueryService.getProteaseByShortName(iPeptideFormBean.getProteaseName());

            //create peptide generator for protease
            logger.info("creating peptide generator");
            PeptideGenerator lGenerator = lSigPepSession.createPeptideGenerator(aProtease);

            //get peptides generated by protease
            logger.info("generating lBackgroundPeptides");
            Set<Peptide> lBackgroundPeptides = lGenerator.getPeptides();

            /*logger.info("generating signature peptides");
            Set<Peptide> lSignaturepeptides = lGenerator.getPeptidesByProteinAccessionAndProteinSequenceLevelDegeneracy(iPeptideFormBean.getProteinAccession(), 1);
            for (Peptide peptide : lSignaturepeptides) {
                logger.info(peptide.getSequenceString());
            }*/
            Set<Peptide> lSignaturepeptides = new HashSet<Peptide>();
            lSignaturepeptides.add(iPeptide);


            //create signature transition finder
            logger.info("creating signature transition finder");

            HashSet lChargeStates = new HashSet();
            lChargeStates.add(2);
            lChargeStates.add(3);

            Set<ProductIonType> lTargetProductIonTypes = new HashSet<ProductIonType>();
            lTargetProductIonTypes.add(ProductIonType.Y);

            Set<ProductIonType> lBackgroundProductIonTypes = new HashSet<ProductIonType>();
            lBackgroundProductIonTypes.add(ProductIonType.Y);
            lBackgroundProductIonTypes.add(ProductIonType.B);

            Set<Integer> lProductIonChargeStates = new HashSet<Integer>();
            lProductIonChargeStates.add(1);

            SignatureTransitionFinder finder = lSigPepSession.createSignatureTransitionFinder(
                    lBackgroundPeptides,
                    lTargetProductIonTypes,
                    lBackgroundProductIonTypes,
                    lChargeStates,
                    lProductIonChargeStates,
                    iPeptideFormBean.getMassAccuracy(),
                    iPeptideFormBean.getMinimumCombinationSize(),
                    iPeptideFormBean.getMaximumCombinationSize(),
                    iPeptideFormBean.getSignatureTransitionFinderType());

            logger.info("finding signature transitions");
            List<SignatureTransition> st = finder.findSignatureTransitions(lSignaturepeptides);

            for (SignatureTransition t : st) {
                logger.info("printing peptide " + t.getPeptide().getSequenceString());
                try {
                    OutputStream os = new FileOutputStream(outputFolder.getAbsolutePath() + File.separator + t.getPeptide().getSequenceString() + ".tsv");

                    SignatureTransitionMassMatrix m = new SignatureTransitionMassMatrix(t);
                    m.write(os);
                    os.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            ArrayList lResultFiles = new ArrayList();
            Collections.addAll(lResultFiles, outputFolder.listFiles(new FileFilter() {
                public boolean accept(File aFile) {
                    return aFile.getName().endsWith(".tsv");
                }
            }));

            synchronized (iApplication) {
                //enable form buttons after run
                iSubmitButton.setEnabled(Boolean.TRUE);
                iCancelButton.setEnabled(Boolean.TRUE);

                iFormButtonLayout.removeComponent(iProgressIndicatorLayout);
                iApplication.setResultTableComponent(new ResultsTable(lResultFiles, iApplication, iApplication));
            }

            iApplication.push();
        }
    }

}
