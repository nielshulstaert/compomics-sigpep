package com.compomics.sigpep.webapp.form;

import com.compomics.sigpep.PeptideGenerator;
import com.compomics.sigpep.SigPepSession;
import com.compomics.sigpep.model.Peptide;
import com.compomics.sigpep.model.Protease;
import com.compomics.sigpep.webapp.MyVaadinApplication;
import com.compomics.sigpep.webapp.bean.PeptideFormBean;
import com.compomics.sigpep.webapp.component.CustomProgressIndicator;
import com.compomics.sigpep.webapp.form.factory.PeptideCheckFormFieldFactory;
import com.vaadin.data.Validator;
import com.vaadin.data.util.BeanItem;
import com.vaadin.ui.Button;
import com.vaadin.ui.Form;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Window;
import org.apache.log4j.Logger;
import org.vaadin.dialogs.ConfirmDialog;
import org.vaadin.notifique.Notifique;

import java.util.Set;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: niels
 * Date: 20/09/11
 * Time: 14:15
 * To change this template use File | Settings | File Templates.
 */
public class PeptideCheckForm extends Form {
    private static Logger logger = Logger.getLogger(PeptideCheckForm.class);

    private MyVaadinApplication iApplication;

    private PeptideFormBean iPeptideFormBean;
    private Peptide iFoundPeptide;

    private Vector<String> iOrder;

    private HorizontalLayout iFormButtonLayout;
    private CustomProgressIndicator iCustomProgressIndicator;
    private Button iSubmitButton;
    private Button iResetButton;

    public PeptideCheckForm(String aCaption, MyVaadinApplication aApplication) {
        this.setCaption(aCaption);
        iApplication = aApplication;

        this.setFormFieldFactory(new PeptideCheckFormFieldFactory(iApplication));

        iPeptideFormBean = new PeptideFormBean();
        BeanItem<PeptideFormBean> lBeanItem = new BeanItem<PeptideFormBean>(iPeptideFormBean);
        this.setItemDataSource(lBeanItem);

        iSubmitButton = new Button("Submit", new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent aClickEvent) {
                try {
                    commit();
                    resetValidation();

                    //add custom progress indicator
                    iCustomProgressIndicator = new CustomProgressIndicator("sigpep peptidechecker job is waiting in the processing queue...", 7);
                    iApplication.getNotifique().add(null, iCustomProgressIndicator, Notifique.Styles.MAGIC_BLACK, Boolean.FALSE);


                    //disable form buttons during run
                    iSubmitButton.setEnabled(Boolean.FALSE);
                    iResetButton.setEnabled(Boolean.FALSE);

                    PeptideCheckFormThread lPeptideCheckFormThread = new PeptideCheckFormThread();
                    MyVaadinApplication.getExecutorService().submit(lPeptideCheckFormThread);

                } catch (Validator.InvalidValueException e) {
                    // Failed to commit. The validation errors are
                    // automatically shown to the user.
                }
            }
        });

        iResetButton = new Button("Reset", new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent aClickEvent) {
                resetForm();
            }
        });

        iFormButtonLayout = new HorizontalLayout();
        iFormButtonLayout.setSpacing(Boolean.TRUE);
        iFormButtonLayout.addComponent(iSubmitButton);
        iFormButtonLayout.addComponent(iResetButton);
        this.getFooter().addComponent(iFormButtonLayout);

        iOrder = new Vector();
        iOrder.add("species");
        iOrder.add("proteaseName");
        iOrder.add("peptideSequence");
        this.setOrder();

        this.setImmediate(Boolean.TRUE);

    }

    private void resetValidation() {
        this.setComponentError(null);
        this.setValidationVisible(false);
        this.setOrder();
    }

    private void resetForm() {
        iPeptideFormBean = new PeptideFormBean();
        BeanItem<PeptideFormBean> lBeanItem = new BeanItem<PeptideFormBean>(iPeptideFormBean);
        PeptideCheckForm.this.setItemDataSource(lBeanItem);
        resetValidation();
        iApplication.clearResultTableComponent();
    }

    private void setOrder() {
        this.setVisibleItemProperties(iOrder);
    }

    private class PeptideCheckFormThread extends Thread {

        public void run() {

            SigPepSession lSigPepSession = iApplication.getSigPepSession();

            if (iApplication.getSigPepQueryService() == null) {
                iApplication.setSigPepQueryService(iApplication.getSigPepSession().createSigPepQueryService());
            }

            Protease aProtease = iApplication.getSigPepQueryService().getProteaseByFullName(iPeptideFormBean.getProteaseName());

            //create peptide generator for protease
            iCustomProgressIndicator.proceed("creating peptide generator for protease " + aProtease.getFullName());
            logger.info("creating peptide generator for protease " + aProtease.getFullName());
            PeptideGenerator lGenerator = lSigPepSession.createPeptideGenerator(aProtease);

            //add generator to bean
            iPeptideFormBean.setPeptideGenerator(lGenerator);

            //get peptides
            iCustomProgressIndicator.proceed("generating background peptides");
            logger.info("generating lBackgroundPeptides");
            boolean lIsFound = false;
            Set<Peptide> lBackgroundPeptides = lGenerator.getPeptides();

            //add background peptides to bean
            iPeptideFormBean.setlBackgroundPeptides(lBackgroundPeptides);

            //search peptide in background peptides
            iCustomProgressIndicator.proceed("looking for peptide " + iPeptideFormBean.getPeptideSequence());
            logger.info("looking for peptide " + iPeptideFormBean.getPeptideSequence());
            for (Peptide lPeptide : lBackgroundPeptides) {
                if (lPeptide.getSequenceString().equals(iPeptideFormBean.getPeptideSequence())) {
                    logger.info("peptide found: " + lPeptide.getSequenceString());
                    iFoundPeptide = lPeptide;
                    lIsFound = true;
                    break;
                }
            }

            //show error message if peptide is not found
            if (!lIsFound) {
                iApplication.getMainWindow().showNotification("Peptide not found", "The peptide sequence " + iPeptideFormBean.getPeptideSequence() + "</br>was not found for organism " + iPeptideFormBean.getSpecies().getScientificName() +
                        " and protease " + aProtease.getFullName() + ".", Window.Notification.TYPE_ERROR_MESSAGE);
                resetForm();
            } else {
                //retrieve sequence ID and protein accession sets for the found peptide sequence
                iCustomProgressIndicator.proceed("Verifying if found peptide is a signature peptide");
                logger.info("Verifying if found peptide is a signature peptide");
                Set<Integer> lSequenceIds = lGenerator.getSequenceIdsByPeptideSequence(iFoundPeptide.getSequenceString());
                Set<String> lProteinAccessions = iApplication.getSigPepQueryService().getProteinAccessionsBySequenceIds(lSequenceIds);

                String lMessage = "";
                if (lProteinAccessions.size() > 1) {
                    lMessage = "The peptide " + iFoundPeptide.getSequenceString() + " is found in proteins " + getSetAsString(lProteinAccessions) + " for organism " + iPeptideFormBean.getSpecies().getScientificName() + " and protease " + iPeptideFormBean.getProteaseName() + ".";
                } else if (lProteinAccessions.size() == 1) {
                    lMessage = "The peptide " + iFoundPeptide.getSequenceString() + " is found in protein " + getSetAsString(lProteinAccessions) + " for organism " + iPeptideFormBean.getSpecies().getScientificName() + " and protease " + iPeptideFormBean.getProteaseName() + ".";
                } else {
                    lMessage = "An unexpected error occurred. Please try again.";
                }

                ConfirmDialog lConfirmDialog = ConfirmDialog.show(iApplication.getMainWindow(), "Confirmation",
                        lMessage + " \n\n Continue?", "Yes", "No",
                        new ConfirmDialog.Listener() {

                            public void onClose(ConfirmDialog dialog) {
                                if (dialog.isConfirmed()) {
                                    // Confirmed to continue
                                    iApplication.getFormTabSheet().proceedPeptideForm(iPeptideFormBean, iFoundPeptide);
                                } else {
                                    resetForm();
                                }
                                synchronized (iApplication) {
                                    //enable form buttons after run
                                    iSubmitButton.setEnabled(Boolean.TRUE);
                                    iResetButton.setEnabled(Boolean.TRUE);

                                    iApplication.getNotifique().clear();
                                }
                                iApplication.push();
                            }
                        });
            }
        }
    }

    private String getSetAsString(Set<String> aStringSet) {
        String lSetContent = "";
        for (String s : aStringSet) {
            lSetContent += s + ", ";
            logger.info(lSetContent);
        }
        lSetContent = lSetContent.substring(0, lSetContent.lastIndexOf(", ") - 1);
        logger.info(lSetContent);
        return lSetContent;
    }

}
