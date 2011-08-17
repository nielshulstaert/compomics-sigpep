/*
 * Copyright 2009 IT Mill Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.compomics.sigpep.webapp;

import com.compomics.sigpep.webapp.form.MainForm;
import com.vaadin.Application;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Label;
import com.vaadin.ui.Window;
import org.vaadin.artur.icepush.ICEPush;

/**
 * The Application's "main" class
 */
@SuppressWarnings("serial")
public class MyVaadinApplication extends Application {

    private ICEPush pusher = new ICEPush();

    @Override
    public void init() {
        Window mainWindow = new Window("Icepushaddon Application");
        setMainWindow(mainWindow);

        mainWindow.addComponent(new MainForm());

        // Add the push component
        mainWindow.addComponent(pusher);

        // Add a button for starting background work
        getMainWindow().addComponent(
                new Button("Do stuff in the background", new Button.ClickListener() {
                    //@Override
                    public void buttonClick(ClickEvent event) {
                        getMainWindow()
                                .addComponent(
                                        new Label(
                                                "Waiting for background process to complete..."));
                        new BackgroundThread().start();

                    }
                }));

    }

    public class BackgroundThread extends Thread {

        @Override
        public void run() {
            // Simulate background work
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }

            // Update UI
            synchronized (MyVaadinApplication.this) {
                getMainWindow().addComponent(new Label("All done"));
            }

            // Push the changes
            pusher.push();
        }

    }
}