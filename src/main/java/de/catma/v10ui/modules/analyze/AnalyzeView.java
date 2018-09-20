package de.catma.v10ui.modules.analyze;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;

import java.time.Instant;

/**
 *
 * A dummy View for now
 * @author db
 */
@Tag("AnalyzeView")
public class AnalyzeView extends Div implements HasComponents{

    public AnalyzeView() {
        initComponents();
    }

    private void initComponents(){
        Button testButton = new Button("Dummy " + Instant.now());
        add(testButton);
    }
}
