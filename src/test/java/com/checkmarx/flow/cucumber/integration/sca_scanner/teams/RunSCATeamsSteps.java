package com.checkmarx.flow.cucumber.integration.sca_scanner.teams;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = { "pretty", "summary", "html:build/cucumber/integration/sca", "json:build/cucumber/integration/sca/cucumber.json" },
        features = "src/test/resources/cucumber/features/integrationTests/sca/teams.feature",
        tags = "not @Skip")
public class RunSCATeamsSteps {
}