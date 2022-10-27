package de.catma.ui.module.dashboard;

import com.google.common.eventbus.EventBus;
import com.vaadin.ui.VerticalLayout;

import de.catma.project.ProjectsManager;
import de.catma.repository.git.managers.interfaces.RemoteGitManagerRestricted;

/**
 *
 * Renders a dashboard with all projects and all shared resources
 *
 * @author db
 */
public class DashboardView extends VerticalLayout {

    public DashboardView(
    		ProjectsManager projectManager,
    		RemoteGitManagerRestricted remoteGitManagerRestricted,
    		EventBus eventBus) { 
    	
        initComponents(projectManager, eventBus, remoteGitManagerRestricted);
    }

    private void initComponents(ProjectsManager projectManager, EventBus eventBus, RemoteGitManagerRestricted remoteGitManagerRestricted) {
    	setSizeFull();
    	setMargin(false);
    	addStyleName("dashboard-view");
    	ProjectListView projectListView = 
    			new ProjectListView(projectManager, eventBus, remoteGitManagerRestricted);
        addComponent(projectListView);
    }

}
