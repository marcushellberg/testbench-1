package com.vaadin.tests;

import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;

import com.vaadin.flow.component.Component;
import com.vaadin.testUI.SVGView;

public class SVGIT extends AbstractTB6Test {

    @Override
    protected Class<? extends Component> getTestView() {
        return SVGView.class;
    }

    @Test
    public void click() {
        openTestURL();
        findElement(By.id("ball")).click();
        Assert.assertEquals("clicked",
                findElement(By.tagName("body")).getText());
    }
}
