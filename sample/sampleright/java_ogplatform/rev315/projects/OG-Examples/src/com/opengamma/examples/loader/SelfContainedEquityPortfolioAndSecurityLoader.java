/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.examples.loader;

import static com.opengamma.examples.loader.SelfContainedSwapPortfolioLoader.getWithException;
import static com.opengamma.examples.loader.SelfContainedSwapPortfolioLoader.normaliseHeaders;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.time.calendar.LocalDate;

import org.apache.commons.lang.math.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import au.com.bytecode.opencsv.CSVReader;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.core.security.SecurityUtils;
import com.opengamma.financial.security.equity.EquitySecurity;
import com.opengamma.financial.security.equity.GICSCode;
import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.master.portfolio.ManageablePortfolio;
import com.opengamma.master.portfolio.ManageablePortfolioNode;
import com.opengamma.master.portfolio.PortfolioDocument;
import com.opengamma.master.position.ManageablePosition;
import com.opengamma.master.position.ManageableTrade;
import com.opengamma.master.position.PositionDocument;
import com.opengamma.master.security.SecurityDocument;
import com.opengamma.master.security.SecurityMaster;
import com.opengamma.util.PlatformConfigUtils;
import com.opengamma.util.PlatformConfigUtils.RunMode;
import com.opengamma.util.money.Currency;

/**
 * Example code to load a very simple equity portfolio.
 * <p>
 * This code is kept deliberately as simple as possible.  There are no checks for the securities or portfolios already existing, so if you run it 
 * more than once you will get multiple copies portfolios and securities with the same names.  It is designed to run against the HSQLDB example
 * database.  It should be possible to run this class with no extra parameters.
 */
public class SelfContainedEquityPortfolioAndSecurityLoader {

  /** Logger. */
  private static final Logger s_logger = LoggerFactory.getLogger(SelfContainedEquityPortfolioAndSecurityLoader.class);

  private static final Map<String, String> SECTORS = new HashMap<String, String>();
  static {
    SECTORS.put("10", "10 Energy");
    SECTORS.put("15", "15 Materials");
    SECTORS.put("20", "20 Industrials");
    SECTORS.put("25", "25 Consumer discretionary");
    SECTORS.put("30", "30 Consumer staples");
    SECTORS.put("35", "35 Health care");
    SECTORS.put("40", "40 Financials");
    SECTORS.put("45", "45 Information technology");
    SECTORS.put("50", "50 Telecommunication");
    SECTORS.put("55", "55 Utilities");
  }
  
  /**
   * The name of the portfolio.
   */
  public static final String PORTFOLIO_NAME = "Self Contained Equity Portfolio";

  /**
   * The context.
   */
  private LoaderContext _loaderContext;

  //-------------------------------------------------------------------------
  /**
   * Sets the loader context.
   * <p>
   * This initializes this bean, typically from Spring.
   * 
   * @param loaderContext  the context, not null
   */
  public void setLoaderContext(final LoaderContext loaderContext) {
    _loaderContext = loaderContext;
  }
  
  /**
   * Gets the loader context.
   * <p>
   * This lets us access the masters that should have been initialized via Spring.
   * @return the loader context
   */
  public LoaderContext getLoaderContext() {
    return _loaderContext;
  }

  /**
   * Loads the test portfolio into the position master.
   */
  public void createExamplePortfolio() {
    // load all equity securities
    final Collection<EquitySecurity> securities = createAndPersistEquitySecurities();
    
    // create shell portfolio
    final ManageablePortfolio portfolio = createEmptyPortfolio();
    final ManageablePortfolioNode rootNode = portfolio.getRootNode();
    
    // add each security to the portfolio
    for (EquitySecurity security : securities) {
      
      GICSCode gics = security.getGicsCode();
      if (gics == null) {
        continue;
      }
      String sector = SECTORS.get(Integer.toString(gics.getSectorCode()));
      String industryGroup = Integer.toString(gics.getIndustryGroupCode());
      String industry = Integer.toString(gics.getIndustryCode());
      String subIndustry = Integer.toString(gics.getSubIndustryCode());
      
      // create portfolio structure
      ManageablePortfolioNode sectorNode = rootNode.findNodeByName(sector);
      if (sectorNode == null) {
        s_logger.warn("Creating node for sector {}", sector);
        sectorNode = new ManageablePortfolioNode(sector);
        rootNode.addChildNode(sectorNode);
      }
      ManageablePortfolioNode groupNode = sectorNode.findNodeByName("Group " + industryGroup);
      if (groupNode == null) {
        s_logger.warn("Creating node for industry group {}", industryGroup);
        groupNode = new ManageablePortfolioNode("Group " + industryGroup);
        sectorNode.addChildNode(groupNode);
      }
      ManageablePortfolioNode industryNode = groupNode.findNodeByName("Industry " + industry);
      if (industryNode == null) {
        s_logger.warn("Creating node for industry {}", industry);
        industryNode = new ManageablePortfolioNode("Industry " + industry);
        groupNode.addChildNode(industryNode);
      }
      ManageablePortfolioNode subIndustryNode = industryNode.findNodeByName("Sub industry " + subIndustry);
      if (subIndustryNode == null) {
        s_logger.warn("Creating node for sub industry {}", subIndustry);
        subIndustryNode = new ManageablePortfolioNode("Sub industry " + subIndustry);
        industryNode.addChildNode(subIndustryNode);
      }
      
      // create the position and add it to the master
      final ManageablePosition position = createPositionAndTrade(security);
      final PositionDocument addedPosition = addPosition(position);
      
      // add the position reference (the unique identifier) to portfolio
      subIndustryNode.addPosition(addedPosition.getUniqueId());
    }
    
    // adds the complete tree structure to the master
    addPortfolio(portfolio);
  }

  protected EquitySecurity createEquitySecurity(String companyName, Currency currency, String exchange, String exchangeCode, int gicsCode, ExternalId... identifiers) {
    EquitySecurity equitySecurity = new EquitySecurity(exchange, exchangeCode, companyName, currency);
    equitySecurity.setGicsCode(GICSCode.getInstance(gicsCode));
    equitySecurity.setIdentifiers(ExternalIdBundle.of(identifiers));
    equitySecurity.setName(companyName);
    return equitySecurity;
  }
  /**
   * Creates securities and adds them to the master.
   * 
   * @return a collection of all securities that have been persisted, not null
   */
  protected Collection<EquitySecurity> createAndPersistEquitySecurities() {
    SecurityMaster secMaster = _loaderContext.getSecurityMaster();
    Collection<EquitySecurity> securities = loadEquitySecurities();
    for (EquitySecurity security : securities) {
      SecurityDocument doc = new SecurityDocument(security);
      secMaster.add(doc);
    }
    return securities;
  }

  private Collection<EquitySecurity> loadEquitySecurities() {
    Collection<EquitySecurity> equities = new ArrayList<EquitySecurity>();
    InputStream inputStream = SelfContainedEquityPortfolioAndSecurityLoader.class.getResourceAsStream("demo-equity.csv");  
    try {
      if (inputStream != null) {
        CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream));
        
        String[] headers = csvReader.readNext();
        normaliseHeaders(headers);
        
        String[] line;
        int rowIndex = 1;
        while ((line = csvReader.readNext()) != null) {
          Map<String, String> equityDetails = new HashMap<String, String>();
          for (int i = 0; i < headers.length; i++) {
            if (i >= line.length) {
              // Run out of headers for this line
              break;
            }
            equityDetails.put(headers[i], line[i]);
          }
          try {
            equities.add(parseEquity(equityDetails));
          } catch (Exception e) {
            s_logger.warn("Skipped row " + rowIndex + " because of an error", e);
          }
          rowIndex++;
        }
      }
    } catch (FileNotFoundException ex) {
      throw new OpenGammaRuntimeException("File '" + inputStream + "' could not be found");
    } catch (IOException ex) {
      throw new OpenGammaRuntimeException("An error occurred while reading file '" + inputStream + "'");
    }
    
    StringBuilder sb = new StringBuilder();
    sb.append("Parsed ").append(equities.size()).append(" equities:\n");
    for (EquitySecurity equity : equities) {
      sb.append("\t").append(equity.getName()).append("\n");
    }
    s_logger.info(sb.toString());
    
    return equities;
  }

  private EquitySecurity parseEquity(Map<String, String> equityDetails) {
    String companyName = getWithException(equityDetails, "companyname");
    String currency = getWithException(equityDetails, "currency");
    String exchange = getWithException(equityDetails, "exchange");
    String exchangeCode = getWithException(equityDetails, "exchangecode");
    String gisCode = getWithException(equityDetails, "giscode");
    String isin = getWithException(equityDetails, "isin");
    String cusip = getWithException(equityDetails, "cusip");
    String ticker = getWithException(equityDetails, "ticker");
    
    return createEquitySecurity(companyName, Currency.of(currency), exchange, exchangeCode, Integer.parseInt(gisCode), 
        ExternalId.of(SecurityUtils.ISIN, isin), 
        ExternalId.of(SecurityUtils.CUSIP, cusip), 
        ExternalId.of(SecurityUtils.OG_SYNTHETIC_TICKER, ticker));
  }

  /**
   * Create a empty portfolio.
   * <p>
   * This creates the portfolio and the root of the tree structure that holds the positions.
   * Subsequent methods then populate the tree.
   * 
   * @return the emoty portfolio, not null
   */
  protected ManageablePortfolio createEmptyPortfolio() {
    ManageablePortfolio portfolio = new ManageablePortfolio(PORTFOLIO_NAME);
    ManageablePortfolioNode rootNode = portfolio.getRootNode();
    rootNode.setName("Root");
    return portfolio;
  }

  /**
   * Create a position of a random number of shares.
   * <p>
   * This creates the position using a random number of units and create one or two trades making up the position.
   * 
   * @param security  the security to add a position for, not null
   * @return the position, not null
   */
  protected ManageablePosition createPositionAndTrade(EquitySecurity security) {
    s_logger.warn("Creating position {}", security);
    int shares = (RandomUtils.nextInt(490) + 10) * 10;
    
    ExternalIdBundle bundle = security.getIdentifiers(); // we could add an identifier pointing back to the original source database if we're doing an ETL.

    ManageablePosition position = new ManageablePosition(BigDecimal.valueOf(shares), bundle);
    
    // create random trades that add up in shares to the position they're under (this is not enforced by the system)
    if (shares <= 2000) {
      ManageableTrade trade = new ManageableTrade(BigDecimal.valueOf(shares), bundle, LocalDate.of(2010, 12, 3), null, ExternalId.of("CPARTY", "BACS"));
      position.addTrade(trade);
    } else {
      ManageableTrade trade1 = new ManageableTrade(BigDecimal.valueOf(2000), bundle, LocalDate.of(2010, 12, 1), null, ExternalId.of("CPARTY", "BACS"));
      position.addTrade(trade1);
      ManageableTrade trade2 = new ManageableTrade(BigDecimal.valueOf(shares - 2000), bundle, LocalDate.of(2010, 12, 2), null, ExternalId.of("CPARTY", "BACS"));
      position.addTrade(trade2);
    }
    return position;
  }

  /**
   * Adds the position to the master.
   * 
   * @param position  the position to add, not null
   * @return the added document, not null
   */
  protected PositionDocument addPosition(ManageablePosition position) {
    return _loaderContext.getPositionMaster().add(new PositionDocument(position));
  }

  /**
   * Adds the portfolio to the master.
   * 
   * @param portfolio  the portfolio to add, not null
   * @return the added document, not null
   */
  protected PortfolioDocument addPortfolio(ManageablePortfolio portfolio) {
    return _loaderContext.getPortfolioMaster().add(new PortfolioDocument(portfolio));
  }

  //-------------------------------------------------------------------------
  /**
   * Sets up and loads the database.
   * <p>
   * This loader requires a Spring configuration file that defines the security,
   * position and portfolio masters, together with an instance of this bean
   * under the name "selfContainedEquityPortfolioAndSecurityLoader".
   * 
   * @param args  the arguments, unused
   */
  public static void main(String[] args) {  // CSIGNORE
    try {
      LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(lc);
      lc.reset(); 
      configurator.doConfigure("src/com/opengamma/examples/server/logback.xml");
      
      // Set the run mode to EXAMPLE so we use the HSQLDB example database.
      PlatformConfigUtils.configureSystemProperties(RunMode.EXAMPLE);
      System.out.println("Starting connections");
      AbstractApplicationContext appContext = new ClassPathXmlApplicationContext("demoPortfolioLoader.xml");
      appContext.start();
      
      try {
        SelfContainedEquityPortfolioAndSecurityLoader loader = (SelfContainedEquityPortfolioAndSecurityLoader) appContext.getBean("selfContainedEquityPortfolioAndSecurityLoader");
        System.out.println("Loading data");
        loader.createExamplePortfolio();
      } finally {
        appContext.close();
      }
      System.out.println("Finished");
      
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    System.exit(0);
  }

}
