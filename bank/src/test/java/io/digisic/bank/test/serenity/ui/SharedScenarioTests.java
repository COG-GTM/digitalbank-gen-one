package io.digisic.bank.test.serenity.ui;

import java.util.HashMap;
import java.util.Map;
import org.openqa.selenium.remote.RemoteWebDriver;

import io.cucumber.java.Status;
import io.cucumber.java.Scenario;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.digisic.bank.test.serenity.common.TestDataService;
import io.digisic.bank.test.serenity.ui.steps.LoginSteps;
import io.digisic.bank.test.serenity.ui.steps.MenuNavigationSteps;
import io.digisic.bank.test.serenity.util.BlazeGridDriver;
import net.serenitybdd.annotations.Steps;
import net.thucydides.model.util.EnvironmentVariables;
import net.thucydides.model.environment.SystemEnvironmentVariables;
import net.thucydides.core.webdriver.ThucydidesWebDriverSupport;

public class SharedScenarioTests {
	
	// User Profile Menu Options
	private final static String UserProfileLogout = "Logout";
	private final static String UserProfileChangePassword = "Change Password";
	private final static String password = "Demo123!";
	
	// Environment 
	private EnvironmentVariables env = SystemEnvironmentVariables.createEnvironmentVariables();
	private String remoteProviderDriver = env.getProperty("webdriver.provided.mydriver");
	private String blazeGridDriver = BlazeGridDriver.class.getName();
	
	// Remote Driver used when testing with Selenium Grid
	private RemoteWebDriver remoteDriver = ThucydidesWebDriverSupport.getProxiedDriver();
	
	@Steps
	private LoginSteps login;
	
	@Steps
	private MenuNavigationSteps menu;
	
	@Steps
	private TestDataService data;
	
	
	@Before
	public void setupTest(Scenario scenario) {
		
		// Only execute if we are executing against BlazeGrid
		if (remoteProviderDriver != null && remoteProviderDriver.equals(blazeGridDriver)) {
			
			if (remoteDriver != null) {
				
				 Map<String, String> map = new HashMap<>();
		         map.put("testCaseName", scenario.getName());
		         map.put("testSuiteName", scenario.getUri().getPath().substring(scenario.getUri().getPath().lastIndexOf("/") + 1, scenario.getUri().getPath().lastIndexOf(".")));
		         
		         remoteDriver.executeAsyncScript("/* FLOW_MARKER test-case-start */", map);
			}
		}
	}
	
	@After
	public void tearDownTest(Scenario scenario) {
		
		// Only execute if we are executing against BlazeGrid
		if (remoteProviderDriver != null && remoteProviderDriver.equals(blazeGridDriver)) {
			
			if (remoteDriver != null) {
		
				Map<String, String> map = new HashMap<>();
				
				if (scenario.getStatus().equals(Status.FAILED)){
					map.put("status", "failed");
	                map.put("message", scenario.getStatus().toString());
				} 
				else if (scenario.getStatus().equals(Status.PASSED)) {
					map.put("status", "passed");
	                map.put("message", scenario.getStatus().toString());
				} else {
					map.put("status", "broken");
	                map.put("message", scenario.getStatus().toString());
				}
				remoteDriver.executeAsyncScript("/* FLOW_MARKER test-case-stop */", map);
				
				// Close the driver!
				remoteDriver.quit();
			} // end if remote driver
		} // end if blaze grid
	}
	
	
	
	/** Given **/
	
	@Given ("^(.*) is logged into the application with (.*)$")
	public void authenticatedUser(String persona, String email) {
		
		if (!data.validateRegisteredUser(persona, email))
			data.registerUser();
		
		// Login with the identified persona that can authenticate
		login.navigateToLoginPage();
		login.enterUsername(email);
		login.enterPassword(password);
		login.clickSubmit();
	}
	
	/** When **/
	
	@When ("^(.*) navigates to the User Profile menu$")
	public void clickUserProfileMenu(String persona) {
		menu.clickUserProfileMenu();
	}
	
	@And ("^they select '(.*)' from the User Profile menu$")
	public void clickUserProfileMenuOption(String option) {
		
		switch (option){
			case UserProfileLogout:
				menu.clickLogoutMenuOption();
				break;
			case UserProfileChangePassword:
				menu.clickChangePasswordMenuOption();
			default:
				// Do not assume a menu option
				break;
		}
		
	}
	
	@And ("^they verify they are at the Home page$")
	public void verifyLocationHomePage() {
		login.redirectedToHomePage();
	}

}
