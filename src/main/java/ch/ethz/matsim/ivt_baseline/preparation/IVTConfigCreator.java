package ch.ethz.matsim.ivt_baseline.preparation;

import ch.ethz.matsim.ivt_baseline.modification.LiteratureScoring;
import ch.ethz.matsim.ivt_baseline.replanning.BlackListedTimeAllocationMutatorConfigGroup;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.locationchoice.DestinationChoiceConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.facilities.algorithms.WorldConnectLocations;
import ch.ethz.matsim.ivt_baseline.preparation.crossborderCreation.CreateCBPop;
import ch.ethz.matsim.ivt_baseline.preparation.freightCreation.CreateFreightTraffic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates a default config for the ivt baseline scenarios.
 *
 * @author boescpa
 */
public class IVTConfigCreator {
	static public final String DIFF_FIRST_LAST_TAG = "diff_first_last";

	// Scenario
    protected final static int NUMBER_OF_THREADS = 8;
    protected final static String INBASE_FILES = "";
	private static final int NUMBER_OF_ITERATIONS = 1000;
    protected final static int WRITE_OUT_INTERVAL = 100;
    protected final static String COORDINATE_SYSTEM = "CH1903_LV03_Plus";

	// ActivityTypes
	public static final String HOME = "home";
	public static final String REMOTE_HOME = "remote_home";
	public static final String WORK = "work";
	public static final String REMOTE_WORK = "remote_work";
	public static final String EDUCATION = "education";
	public static final String LEISURE = "leisure";
	public static final String SHOP = "shop";
	public static final String ESCORT_KIDS = "escort_kids";
	public static final String ESCORT_OTHER = "escort_other";

	// Files
    public static final String FACILITIES = "facilities.xml.gz";
    public static final String HOUSEHOLD_ATTRIBUTES = "household_attributes.xml.gz";
    public static final String HOUSEHOLDS = "households.xml.gz";
    public static final String POPULATION = "population.xml.gz";
    public static final String POPULATION_ATTRIBUTES = "population_attributes.xml.gz";
    public static final String NETWORK = "mmNetwork.xml.gz";
    public static final String SCHEDULE = "mmSchedule.xml.gz";
    public static final String VEHICLES = "mmVehicles.xml.gz";
    public static final String FACILITIES2LINKS = "facilitiesLinks.f2l";

	public static void main(String[] args) {
        int prctScenario = Integer.parseInt(args[1]); // the percentage of the scenario in percent (e.g. 1%-Scenario -> "1")
        // Create config and add kti-scoring and destination choice
        Config config = ConfigUtils.createConfig();
        new IVTConfigCreator().makeConfigIVT(config, prctScenario);
		new LiteratureScoring().adjustScoring(config.planCalcScore());
        new ConfigWriter(config).write(args[0]);
    }

    public void makeConfigIVT(Config config, final int prctScenario) {
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory("output");
        // Set the number of iterations
		config.controler().setLastIteration(NUMBER_OF_ITERATIONS);
		// Correct routing algorithm
		config.controler().setRoutingAlgorithmType(ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks);
        // Change write out intervals
		config.controler().setWriteEventsInterval(WRITE_OUT_INTERVAL);
		config.controler().setWritePlansInterval(WRITE_OUT_INTERVAL);
		config.controler().setWriteSnapshotsInterval(WRITE_OUT_INTERVAL);
		config.counts().setWriteCountsInterval(WRITE_OUT_INTERVAL);
		config.ptCounts().setPtCountsInterval(WRITE_OUT_INTERVAL);
		config.linkStats().setWriteLinkStatsInterval(WRITE_OUT_INTERVAL);
        // Set coordinate system
		config.global().setCoordinateSystem(COORDINATE_SYSTEM);
        // Set end time
		config.qsim().setEndTime(108000); // 30:00:00
		// Set stuck time
		config.qsim().setStuckTime(600); // 00:10:00
        // Add strategies
        List<StrategyConfigGroup.StrategySettings> strategyDescrs = getStrategyDescr();
        for (StrategyConfigGroup.StrategySettings strategy : strategyDescrs) {
            config.getModule(StrategyConfigGroup.GROUP_NAME).addParameterSet(strategy);
        }
		// Add black listed time mutation and the black listed modes:
		// (black listed are all modes which are not free for the agent to decide the start and end times)
		BlackListedTimeAllocationMutatorConfigGroup blTAM = new BlackListedTimeAllocationMutatorConfigGroup();
		Set<String> timeMutationBlackList = new HashSet<>();
		timeMutationBlackList.add(WORK);
		timeMutationBlackList.add(REMOTE_WORK);
		timeMutationBlackList.add(EDUCATION);
		timeMutationBlackList.add(ESCORT_KIDS);
		timeMutationBlackList.add(ESCORT_OTHER);

		for (String type : new String[] { WORK, REMOTE_WORK, EDUCATION, ESCORT_KIDS, ESCORT_OTHER }) {
			for (int i = 0; i < 20; i++) {
				timeMutationBlackList.add(type + "_" + i);
			}
		}

		blTAM.setBlackList(timeMutationBlackList);
		config.addModule(blTAM);
		// Add location choice
		/*DestinationChoiceConfigGroup destChoiConfigGroup = new DestinationChoiceConfigGroup();
		destChoiConfigGroup.setFlexibleTypes("remote_work, leisure, shop, escort_kids, escort_other");
		destChoiConfigGroup.setEpsilonScaleFactors("0.3, 0.1, 0.1, 0.1, 0.2");
		destChoiConfigGroup.setTravelTimeApproximationLevel(DestinationChoiceConfigGroup.ApproximationLevel.localRouting);
		destChoiConfigGroup.setPrefsFile(INBASE_FILES + POPULATION_ATTRIBUTES);
		config.addModule(destChoiConfigGroup);*/
        // Activate transit and correct it to ivt-experience
		config.transit().setUseTransit(true);
		config.planCalcScore().setUtilityOfLineSwitch(-2.0);
		config.transitRouter().setSearchRadius(2000.0);
		config.transitRouter().setAdditionalTransferTime(0.5);
		PlanCalcScoreConfigGroup.ModeParams transitWalkSet = getModeParamsTransitWalk(config);
		transitWalkSet.setMarginalUtilityOfTraveling(-12.0);
		// Add scoring for subpopulations
		PlanCalcScoreConfigGroup.ScoringParameterSet scoringParams = config.planCalcScore().getOrCreateScoringParameters(null);
		scoringParams.getOrCreateActivityParams("cbHome").setScoringThisActivityAtAll(false);
		scoringParams.getOrCreateActivityParams("cbWork").setScoringThisActivityAtAll(false);
		scoringParams.getOrCreateActivityParams("cbShop").setScoringThisActivityAtAll(false);
		scoringParams.getOrCreateActivityParams("cbLeisure").setScoringThisActivityAtAll(false);
		scoringParams.getOrCreateActivityParams("freight").setScoringThisActivityAtAll(false);
        // Set threads to NUMBER_OF_THREADS
		config.global().setNumberOfThreads(NUMBER_OF_THREADS);
		config.parallelEventHandling().setNumberOfThreads(NUMBER_OF_THREADS); // overridden by next line
		config.parallelEventHandling().setOneThreadPerHandler(true);
		config.qsim().setNumberOfThreads(NUMBER_OF_THREADS);
        // Account for prct-scenario
		config.counts().setCountsScaleFactor(100d / prctScenario);
		config.ptCounts().setCountsScaleFactor(100d / prctScenario);

		if (prctScenario == 1) {
			config.qsim().setFlowCapFactor(0.03);
		} else {
			config.qsim().setFlowCapFactor(prctScenario / 100d);
		}

        // Add files
		config.facilities().setInputFile(INBASE_FILES + FACILITIES);
		config.households().setInputFile(INBASE_FILES + HOUSEHOLDS);
		config.households().setInputHouseholdAttributesFile(INBASE_FILES + HOUSEHOLD_ATTRIBUTES);
		config.network().setInputFile(INBASE_FILES + NETWORK);
		config.plans().setInputFile(INBASE_FILES + POPULATION);
		config.plans().setInputPersonAttributeFile(INBASE_FILES + POPULATION_ATTRIBUTES);
		config.transit().setTransitScheduleFile(INBASE_FILES + SCHEDULE);
		config.transit().setVehiclesFile(INBASE_FILES + VEHICLES);

		// Set teleportation speeds /sh (values by pb)
		PlansCalcRouteConfigGroup.ModeRoutingParams walkRoutingParams = config.plansCalcRoute().getModeRoutingParams().get(TransportMode.walk);
		walkRoutingParams.setBeelineDistanceFactor(1.642);
		walkRoutingParams.setTeleportedModeSpeed(4.72 * (1000.0 / 3600.0));

		PlansCalcRouteConfigGroup.ModeRoutingParams walkAccessRoutingParams = config.plansCalcRoute().getModeRoutingParams().get(TransportMode.access_walk);
		walkAccessRoutingParams.setBeelineDistanceFactor(1.642);
		walkAccessRoutingParams.setTeleportedModeSpeed(4.72 * (1000.0 / 3600.0));

		PlansCalcRouteConfigGroup.ModeRoutingParams walkEgressRoutingParams = config.plansCalcRoute().getModeRoutingParams().get(TransportMode.egress_walk);
		walkEgressRoutingParams.setBeelineDistanceFactor(1.642);
		walkEgressRoutingParams.setTeleportedModeSpeed(4.72 * (1000.0 / 3600.0));

		PlansCalcRouteConfigGroup.ModeRoutingParams bikeRoutingParams = config.plansCalcRoute().getModeRoutingParams().get(TransportMode.bike);
		bikeRoutingParams.setBeelineDistanceFactor(1.663);
		bikeRoutingParams.setTeleportedModeSpeed(14.01 * (1000.0 / 3600.0));

		config.subtourModeChoice().setConsiderCarAvailability(true);
    }

	private PlanCalcScoreConfigGroup.ModeParams getModeParamsTransitWalk(Config config) {
		PlanCalcScoreConfigGroup.ModeParams transitWalkSet = config.planCalcScore().getOrCreateModeParams(TransportMode.transit_walk);
		transitWalkSet.setConstant(config.planCalcScore().getOrCreateModeParams(TransportMode.walk).getConstant());
		transitWalkSet.setMarginalUtilityOfDistance(config.planCalcScore().getOrCreateModeParams(TransportMode.walk).getMarginalUtilityOfDistance());
		transitWalkSet.setMarginalUtilityOfTraveling(config.planCalcScore().getOrCreateModeParams(TransportMode.walk).getMarginalUtilityOfTraveling());
		transitWalkSet.setMonetaryDistanceRate(config.planCalcScore().getOrCreateModeParams(TransportMode.walk).getMonetaryDistanceRate());
		return transitWalkSet;
	}

	protected List<StrategyConfigGroup.StrategySettings> getStrategyDescr() {
		List<StrategyConfigGroup.StrategySettings> strategySettings = new ArrayList<>();
		// main pop
		strategySettings.add(getStrategySetting("ChangeExpBeta", 0.6));
		strategySettings.add(getStrategySetting("ReRoute", 0.1));
		strategySettings.add(getStrategySetting("BlackListedTimeAllocationMutator", 0.1));
		strategySettings.add(getStrategySetting("SubtourModeChoice", 0.1));
		//strategySettings.add(getStrategySetting("org.matsim.contrib.locationchoice.BestReplyLocationChoicePlanStrategy", 0.1));
		// cb pop
		strategySettings.add(getStrategySetting("ChangeExpBeta", 0.6, CreateCBPop.CB_TAG));
		strategySettings.add(getStrategySetting("ReRoute", 0.1, CreateCBPop.CB_TAG));
		// freight pop
		strategySettings.add(getStrategySetting("ChangeExpBeta", 0.6, CreateFreightTraffic.FREIGHT_TAG));
		strategySettings.add(getStrategySetting("ReRoute", 0.1, CreateFreightTraffic.FREIGHT_TAG));
		// diff_first_last pop
		strategySettings.add(getStrategySetting("ChangeExpBeta", 0.6, DIFF_FIRST_LAST_TAG));
		strategySettings.add(getStrategySetting("ReRoute", 0.1, DIFF_FIRST_LAST_TAG));
		strategySettings.add(getStrategySetting("SubtourModeChoice", 0.1, DIFF_FIRST_LAST_TAG));
        return strategySettings;
    }

	protected static StrategyConfigGroup.StrategySettings getStrategySetting(String strategyName, double strategyWeight, String subPopulation) {
		StrategyConfigGroup.StrategySettings strategySetting = getStrategySetting(strategyName, strategyWeight);
		strategySetting.setSubpopulation(subPopulation);
		return strategySetting;
	}

	protected static StrategyConfigGroup.StrategySettings getStrategySetting(String strategyName, double strategyWeight) {
		StrategyConfigGroup.StrategySettings strategySetting = new StrategyConfigGroup.StrategySettings();
		strategySetting.setStrategyName(strategyName);
		strategySetting.setWeight(strategyWeight);
		return strategySetting;
	}
}
