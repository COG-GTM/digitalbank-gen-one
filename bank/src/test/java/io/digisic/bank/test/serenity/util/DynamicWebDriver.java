package io.digisic.bank.test.serenity.util;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;

import net.thucydides.core.webdriver.DriverSource;
import net.thucydides.model.environment.SystemEnvironmentVariables;
import net.thucydides.model.util.EnvironmentVariables;

/**
 * Creates the local browser selected by {@code dynamic.webdriver.driver} (chrome, firefox, edge, ie).
 * Browser drivers are resolved by Selenium Manager; {@code dynamic.webdriver.headless} (default true)
 * runs Chrome, Firefox and Edge headless. Self-signed certificates are accepted for the HTTPS app.
 */
public class DynamicWebDriver implements DriverSource {

	private static final String WINDOW_SIZE = "1920,1080";

	private final EnvironmentVariables env = SystemEnvironmentVariables.createEnvironmentVariables();

	@Override
	public WebDriver newDriver() {

		String driverType = env.getProperty("dynamic.webdriver.driver", "chrome");
		boolean headless = Boolean.parseBoolean(env.getProperty("dynamic.webdriver.headless", "true"));

		switch (driverType.toUpperCase()) {
		case "FIREFOX":
			FirefoxOptions firefoxOptions = new FirefoxOptions();
			firefoxOptions.setAcceptInsecureCerts(true);
			if (headless) {
				firefoxOptions.addArguments("-headless", "--width=1920", "--height=1080");
			}
			return new FirefoxDriver(firefoxOptions);

		case "EDGE":
			EdgeOptions edgeOptions = new EdgeOptions();
			edgeOptions.setAcceptInsecureCerts(true);
			if (headless) {
				edgeOptions.addArguments("--headless=new", "--window-size=" + WINDOW_SIZE);
			}
			return new EdgeDriver(edgeOptions);

		case "IE":
			InternetExplorerOptions ieOptions = new InternetExplorerOptions();
			ieOptions.setAcceptInsecureCerts(true);
			return new InternetExplorerDriver(ieOptions);

		default:
			ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.setAcceptInsecureCerts(true);
			chromeOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--no-first-run",
					"--window-size=" + WINDOW_SIZE);
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			String chromeBinary = env.getProperty("dynamic.webdriver.chrome.binary", "");
			if (!chromeBinary.isBlank()) {
				chromeOptions.setBinary(chromeBinary);
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	@Override
	public boolean takesScreenshots() {
		return true;
	}
}
