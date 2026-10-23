package io.digisic.bank.test.serenity;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/api")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,io.cucumber.core.plugin.SerenityReporterParallel")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "io.digisic.bank.test.serenity.api,io.digisic.bank.test.serenity.common")
public class ApiAcceptanceTests {

}
