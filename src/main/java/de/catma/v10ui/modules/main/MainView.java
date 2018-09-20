package de.catma.v10ui.modules.main;

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import de.catma.v10ui.routing.Routes;
import de.catma.v10ui.util.Version;

/**
 * Main entrypoint for catma, it renders a navigation and a dashboard
 *
 * @author db
 */
@Route(Routes.ROOT)
@PageTitle("Catma Main")
public class MainView extends Div implements RouterLayout, HasComponents {

    /**
     * Keep version information in VCS
     */
    private final Header header = new Header(new H2("Catma " + Version.LATEST));
    /**
     * dashboard is the main section of catma
     */
    private final Section dashboard = new Section();

    /**
     * left side main navigation
     */
    private final CatmaNav navigation = new CatmaNav();


    public MainView() {
        initComponents();
    }


    /**
     * initialize all components
     */
    private void initComponents() {
        this.add(header);
        this.add(navigation);
        this.add(dashboard);

    }

}
