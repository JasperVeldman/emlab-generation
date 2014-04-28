/*******************************************************************************
 * Copyright 2012 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package emlab.gen.domain.technology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.annotation.Transient;
import org.springframework.data.neo4j.annotation.NodeEntity;
import org.springframework.data.neo4j.annotation.RelatedTo;
import org.springframework.transaction.annotation.Transactional;

import emlab.gen.domain.agent.EnergyProducer;
import emlab.gen.domain.contract.Loan;
import emlab.gen.domain.market.electricity.ElectricitySpotMarket;
import emlab.gen.domain.market.electricity.PowerPlantDispatchPlan;
import emlab.gen.domain.market.electricity.Segment;
import emlab.gen.domain.market.electricity.SegmentLoad;
import emlab.gen.repository.MarketRepository;
import emlab.gen.repository.PowerPlantDispatchPlanRepository;

/**
 * Representation of a power plant
 * 
 * @author jcrichstein
 * @author ejlchappin
 * 
 */

@Configurable
@NodeEntity
public class PowerPlant {

    @Transient
    @Autowired
    private PowerPlantDispatchPlanRepository powerPlantDispatchPlanRepository;

    @Transient
    @Autowired
    private MarketRepository marketRepository;

    @RelatedTo(type = "TECHNOLOGY", elementClass = PowerGeneratingTechnology.class, direction = Direction.OUTGOING)
    private PowerGeneratingTechnology technology;

    @RelatedTo(type = "FUEL_MIX", elementClass = SubstanceShareInFuelMix.class, direction = Direction.OUTGOING)
    private Set<SubstanceShareInFuelMix> fuelMix;

    @RelatedTo(type = "POWERPLANT_OWNER", elementClass = EnergyProducer.class, direction = Direction.OUTGOING)
    private EnergyProducer owner;

    @RelatedTo(type = "LOCATION", elementClass = PowerGridNode.class, direction = Direction.OUTGOING)
    private PowerGridNode location;

    @RelatedTo(type = "LOAN", elementClass = Loan.class, direction = Direction.OUTGOING)
    private Loan loan;

    @RelatedTo(type = "DOWNPAYMENT", elementClass = Loan.class, direction = Direction.OUTGOING)
    private Loan downpayment;

    /**
     * dismantleTime is set to 1000 as a signifier, that the powerplant is not
     * yet dismantled.
     */
    private long dismantleTime;
    private long constructionStartTime;
    private long actualLeadtime;
    private long actualPermittime;
    private long actualLifetime;
    private String name;
    private String label;
    private double actualInvestedCapital;
    private double actualFixedOperatingCost;
    private double actualEfficiency;
    private double expectedEndOfLife;
    private double actualNominalCapacity;

    public boolean isOperational(long currentTick) {

        double finishedConstruction = getConstructionStartTime() + calculateActualPermittime()
                + calculateActualLeadtime();

        if (finishedConstruction <= currentTick) {
            // finished construction

            if (getDismantleTime() == 1000) {
                // No dismantletime set, therefore must be not yet dismantled.
                return true;
            } else if (getDismantleTime() > currentTick) {
                // Dismantle time set, but not yet reached
                return true;
            } else if (getDismantleTime() <= currentTick) {
                // Dismantle time passed so not operational
                return false;
            }
        }
        // Construction not yet finished.
        return false;
    }

    public boolean isExpectedToBeOperational(long time) {

        double finishedConstruction = getConstructionStartTime() + calculateActualPermittime()
                + calculateActualLeadtime();

        if (finishedConstruction <= time) {
            // finished construction

            if (getExpectedEndOfLife() > time) {
                // Powerplant is not expected to be dismantled
                return true;
            }
        }
        // Construction not yet finished.
        return false;
    }

    public boolean isInPipeline(long currentTick) {

        double finishedConstruction = getConstructionStartTime() + calculateActualPermittime()
                + calculateActualLeadtime();

        if (finishedConstruction > currentTick) {
            // finished construction

            if (getDismantleTime() == 1000) {
                // No dismantletime set, therefore must be not yet dismantled.
                return true;
            } else if (getDismantleTime() > currentTick) {
                // Dismantle time set, but not yet reached
                return true;
            } else if (getDismantleTime() <= currentTick) {
                // Dismantle time passed so not operational
                return false;
            }
        }
        // Construction finished
        return false;
    }

    public double getAvailableCapacity(long currentTick, Segment segment, long numberOfSegments) {
        if (isOperational(currentTick)) {
            double factor = 1;
            if (segment != null) {// if no segment supplied, assume we want full
                // capacity
                double segmentID = segment.getSegmentID();
                if ((int) segmentID != 1) {

                    double min = getTechnology().getPeakSegmentDependentAvailability();
                    double max = getTechnology().getBaseSegmentDependentAvailability();
                    double segmentPortion = (numberOfSegments - segmentID) / (numberOfSegments - 1); // start
                    // counting
                    // at
                    // 1.

                    double range = max - min;

                    factor = max - segmentPortion * range;
                    int i = 0;
                } else {
                    factor = getTechnology().getPeakSegmentDependentAvailability();
                }
            }
            return getActualNominalCapacity() * factor;
        } else {
            return 0;
        }
    }

    public double getExpectedAvailableCapacity(long futureTick, Segment segment, long numberOfSegments) {
        if (isExpectedToBeOperational(futureTick)) {
            double factor = 1;
            if (segment != null) {// if no segment supplied, assume we want full
                // capacity
                double segmentID = segment.getSegmentID();
                double min = getTechnology().getPeakSegmentDependentAvailability();
                double max = getTechnology().getBaseSegmentDependentAvailability();
                double segmentPortion = (numberOfSegments - segmentID) / (numberOfSegments - 1); // start
                // counting
                // at
                // 1.

                double range = max - min;

                factor = max - segmentPortion * range;
            }
            return getActualNominalCapacity() * factor;
        } else {
            return 0;
        }
    }

    public double getAvailableCapacity(long currentTick) {
        if (isOperational(currentTick)) {
            return getActualNominalCapacity();
        } else {
            return 0;
        }
    }

    public long calculateActualLeadtime() {
        long actual;
        actual = getActualLeadtime();
        if (actual <= 0) {
            actual = getTechnology().getExpectedLeadtime();
        }
        return actual;
    }

    public long calculateActualPermittime() {
        long actual;
        actual = getActualPermittime();
        if (actual <= 0) {
            actual = getTechnology().getExpectedPermittime();
        }
        return actual;
    }

    public long calculateActualLifetime() {
        long actual;
        actual = getActualLifetime();
        if (actual <= 0) {
            actual = getTechnology().getExpectedLifetime();
        }
        return actual;
    }

    /**
     * Determines whether a plant is still in its technical lifetime. The end of
     * the technical lifetime is determined by the construction start time, the
     * permit time, the lead time and the actual lifetime.
     * 
     * @param currentTick
     * @return whether the plant is still in its technical lifetime.
     */
    public boolean isWithinTechnicalLifetime(long currentTick) {
        long endOfTechnicalLifetime = getConstructionStartTime() + calculateActualPermittime()
                + calculateActualLeadtime() + calculateActualLifetime();
        if (endOfTechnicalLifetime <= currentTick) {
            return false;
        }
        return true;
    }

    public PowerGridNode getLocation() {
        return location;
    }

    public void setLocation(PowerGridNode location) {
        this.location = location;
    }

    public PowerGeneratingTechnology getTechnology() {
        return technology;
    }

    public void setTechnology(PowerGeneratingTechnology technology) {
        this.technology = technology;
    }

    public long getConstructionStartTime() {
        return constructionStartTime;
    }

    public void setConstructionStartTime(long constructionStartTime) {
        this.constructionStartTime = constructionStartTime;
    }

    public EnergyProducer getOwner() {
        return owner;
    }

    public void setOwner(EnergyProducer owner) {
        this.owner = owner;
    }

    public void setActualLifetime(long actualLifetime) {
        this.actualLifetime = actualLifetime;
    }

    public long getActualLifetime() {
        return actualLifetime;
    }

    public void setActualPermittime(long actualPermittime) {
        this.actualPermittime = actualPermittime;
    }

    public long getActualPermittime() {
        return actualPermittime;
    }

    public void setActualLeadtime(long actualLeadtime) {
        this.actualLeadtime = actualLeadtime;
    }

    public long getActualLeadtime() {
        return actualLeadtime;
    }

    public long getDismantleTime() {
        return dismantleTime;
    }

    public void setDismantleTime(long dismantleTime) {
        this.dismantleTime = dismantleTime;
    }

    public String getName() {
        return label;
    }

    public void setName(String label) {
        this.name = label;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getActualInvestedCapital() {
        return actualInvestedCapital;
    }

    public void setActualInvestedCapital(double actualInvestedCapital) {
        this.actualInvestedCapital = actualInvestedCapital;
    }

    public Set<SubstanceShareInFuelMix> getFuelMix() {
        return fuelMix;
    }

    public void setFuelMix(Set<SubstanceShareInFuelMix> fuelMix) {
        this.fuelMix = fuelMix;
    }

    public Loan getLoan() {
        return loan;
    }

    public void setLoan(Loan loan) {
        this.loan = loan;
    }

    public Loan getDownpayment() {
        return downpayment;
    }

    public void setDownpayment(Loan downpayment) {
        this.downpayment = downpayment;
    }

    public double getActualEfficiency() {
        return actualEfficiency;
    }

    public void setActualEfficiency(double actualEfficiency) {
        this.actualEfficiency = actualEfficiency;
    }

    @Override
    public String toString() {
        return this.getName() + " power plant";
    }

    /**
     * Sets the actual capital that is needed to build the power plant. It reads
     * the investment cost from the and automatically adjusts for the actual
     * building and permit time, as well as power plant size.
     * 
     * @param timeOfPermitorBuildingStart
     */
    public void calculateAndSetActualInvestedCapital(long timeOfPermitorBuildingStart) {
        setActualInvestedCapital(this.getTechnology().getInvestmentCost(
                timeOfPermitorBuildingStart + getActualLeadtime() + getActualPermittime())
                * getActualNominalCapacity());
    }

    public void calculateAndSetActualFixedOperatingCosts(long timeOfPermitorBuildingStart) {
        setActualFixedOperatingCost(this.getTechnology().getFixedOperatingCost(
                timeOfPermitorBuildingStart + getActualLeadtime() + getActualPermittime())
                * getActualNominalCapacity());
    }

    public void calculateAndSetActualEfficiency(long timeOfPermitorBuildingStart) {
        this.setActualEfficiency(this.getTechnology().getEfficiency(
                timeOfPermitorBuildingStart + getActualLeadtime() + getActualPermittime()));
    }

    public double calculateEmissionIntensity() {

        double emission = 0d;
        for (SubstanceShareInFuelMix sub : this.getFuelMix()) {
            Substance substance = sub.getSubstance();
            double fuelAmount = sub.getShare();
            double co2density = substance.getCo2Density() * (1 - this.getTechnology().getCo2CaptureEffciency());

            // determine the total cost per MWh production of this plant
            double emissionForThisFuel = fuelAmount * co2density;
            emission += emissionForThisFuel;
        }

        return emission;
    }

    public double calculateElectricityOutputAtTime(long time, boolean forecast) {
        // TODO This is in MWh (so hours of segment included!!)
        double amount = 0d;
        for (PowerPlantDispatchPlan plan : powerPlantDispatchPlanRepository
                .findAllPowerPlantDispatchPlansForPowerPlantForTime(this, time, forecast)) {
            amount += plan.getSegment().getLengthInHours()
                    * (plan.getCapacityLongTermContract() + plan.getAcceptedAmount());
        }
        return amount;
    }

    public double calculateCO2EmissionsAtTime(long time, boolean forecast) {
        return this.calculateEmissionIntensity() * calculateElectricityOutputAtTime(time, forecast);
    }

    @Transactional
    public void dismantlePowerPlant(long time) {
        this.setDismantleTime(time);
    }

    /**
     * Persists and specifies the properties of a new Power Plant (which needs
     * to be created separately before with new PowerPlant();
     * 
     * Do not forget that any change made here should be reflected in the
     * ElectricityProducerFactory!!
     * 
     * @param time
     * @param energyProducer
     * @param location
     * @param technology
     * 
     * @author J.C.Richstein
     */
    @Transactional
    public void specifyAndPersist(long time, EnergyProducer energyProducer, PowerGridNode location,
            PowerGeneratingTechnology technology) {
        specifyNotPersist(time, energyProducer, location, technology);
        this.persist();
    }

    public void specifyNotPersist(long time, EnergyProducer energyProducer, PowerGridNode location,
            PowerGeneratingTechnology technology) {
        String label = energyProducer.getName() + " - " + technology.getName();
        this.setName(label);
        this.setTechnology(technology);
        this.setOwner(energyProducer);
        this.setLocation(location);
        this.setConstructionStartTime(time);
        this.setActualLeadtime(this.technology.getExpectedLeadtime());
        this.setActualPermittime(this.technology.getExpectedPermittime());
        this.calculateAndSetActualEfficiency(time);
        this.setActualNominalCapacity(this.getTechnology().getCapacity() * location.getCapacityMultiplicationFactor());
        assert this.getActualEfficiency() <= 1 : this.getActualEfficiency();
        this.setDismantleTime(1000);
        this.calculateAndSetActualInvestedCapital(time);
        this.calculateAndSetActualFixedOperatingCosts(time);
        this.setExpectedEndOfLife(time + getActualPermittime() + getActualLeadtime()
                + getTechnology().getExpectedLifetime());
    }

    @Transactional
    public void createOrUpdateLoan(Loan loan) {
        this.setLoan(loan);
    }

    @Transactional
    public void createOrUpdateDownPayment(Loan downpayment) {
        this.setDownpayment(downpayment);
    }

    public double getExpectedEndOfLife() {
        return expectedEndOfLife;
    }

    public void setExpectedEndOfLife(double expectedEndOfLife) {
        this.expectedEndOfLife = expectedEndOfLife;
    }

    @Transactional
    public void updateFuelMix(Set<SubstanceShareInFuelMix> fuelMix) {
        this.setFuelMix(fuelMix);
    }

    /**
     * @return the actualNominalCapacity
     */
    public double getActualNominalCapacity() {
        return actualNominalCapacity;
    }

    /**
     * @param actualNominalCapacity
     *            the actualNominalCapacity to set
     */
    public void setActualNominalCapacity(double actualNominalCapacity) {
        this.actualNominalCapacity = actualNominalCapacity;
    }

    /**
     * @return the actualFixedOperatingCost
     */
    public double getActualFixedOperatingCost() {
        return actualFixedOperatingCost;
    }

    /**
     * @param actualFixedOperatingCost
     *            the actualFixedOperatingCost to set
     */
    public void setActualFixedOperatingCost(double actualFixedOperatingCost) {
        this.actualFixedOperatingCost = actualFixedOperatingCost;
    }

    public double getAvailableHydroPowerCapacity(long currentTick, Segment segment, long numberOfSegments,
            ElectricitySpotMarket market, double intermittentBase, double intermittentPeak, double intermittentTotal,
            double energyConstraint, HashMap<Double, Double> interconnectorA, HashMap<Double, Double> interconnectorB) {
        double segmentID = segment.getSegmentID();
        double sumLoadDifference = 0;
        double energyDeliveredPrevious;
        double energyAvailable;
        double totalHoursModel;
        double segmentLoadOld = 0;
        double segmentLoadNew;
        double loadDifference = 0;
        double remainingLoadDifference;
        double fullHours;
        double loadOfSegment;
        double segmentIDHydro = 0;
        double segmentIDHydroLoad;
        double obtainOldCapacity = 0;
        double availableCapacity = 0;
        double declareNewCapacity = 0;
        double baseLoad;
        double totalCapacity = 0;
        double totalHoursList = 0;
        double interconnectorFlow = 0;
        double fullCapacity;
        double energyDeliveredOfferList = 0;
        double energyDeliveredCapacityList;
        double energyDeliveredTotal = 0;
        double residualLoad = 0;
        boolean capacityConstrained;

        double factor = 0;
        List<Double> loadList = new ArrayList<Double>();
        List<Double> hoursList = new ArrayList<Double>();
        List<Double> sortedHoursList = new ArrayList<Double>();
        List<Double> segmentHoursListCurrent = new ArrayList<Double>();
        List<Double> segmentHoursListOffered = new ArrayList<Double>();
        List<Double> loadDifferenceList = new ArrayList<Double>();
        List<Double> capacityList = new ArrayList<Double>();
        List<Double> offerList = new ArrayList<Double>();

        if (isOperational(currentTick)) {

            loadList.clear();

            for (SegmentLoad segmentload : market.getLoadDurationCurve()) {
                Segment segmentHydroLoad = segmentload.getSegment();
                baseLoad = segmentload.getBaseLoad() * market.getDemandGrowthTrend().getValue(currentTick);
                segmentIDHydroLoad = segmentHydroLoad.getSegmentID();

                // boolean forecast = false;

                if (marketRepository.countAllElectricitySpotMarkets() == 2) {

                    if (currentTick >= 1) {
                        // SegmentClearingPoint scp =
                        // segmentClearingPointRepository
                        // .findOneSegmentClearingPointForMarketSegmentAndTime(currentTick
                        // - 1, segmentHydroLoad,
                        // market, false);

                        // interconnectorFlow = scp.getInterconnectorFlow();

                        if (market.getZone().getName().equals("Country A")) {
                            interconnectorFlow = -1 * interconnectorA.get(segmentID);
                        } else if (market.getZone().getName().equals("Country B")) {
                            interconnectorFlow = -1 * interconnectorB.get(segmentID);
                        }

                    } else {
                        if (market.getZone().getName().equals("Country A")) {
                            interconnectorFlow = -2000;
                        } else if (market.getZone().getName().equals("Country B")) {
                            interconnectorFlow = 2000;
                        }
                    }

                }

                if (segmentIDHydroLoad != numberOfSegments) {

                    double segmentPortionHydro = (numberOfSegments - segmentIDHydroLoad) / (numberOfSegments - 1);
                    double range = intermittentBase - intermittentPeak;

                    factor = intermittentBase - segmentPortionHydro * range;
                    residualLoad = baseLoad - factor * intermittentTotal + interconnectorFlow;
                    if (residualLoad > 0) {
                        loadList.add(residualLoad);
                        hoursList.add(segmentHydroLoad.getLengthInHours());
                    } else {
                        continue;
                    }

                } else {
                    if (residualLoad > 0) {
                        factor = intermittentBase;
                        residualLoad = baseLoad - factor * intermittentTotal + interconnectorFlow;
                        loadList.add(residualLoad);
                        hoursList.add(segmentHydroLoad.getLengthInHours());
                    } else {
                        continue;
                    }

                }

            }

            double count = 0;
            loadList.add(count);
            hoursList.add(count);

            HashMap<Double, Double> sorting = new HashMap<Double, Double>();

            for (int g = 0; g < loadList.size(); g++) {
                sorting.put(loadList.get(g), hoursList.get(g));
            }

            Collections.sort(loadList, Collections.reverseOrder());

            for (int h = 0; h < loadList.size(); h++) {
                double x = loadList.get(h);
                double y = sorting.get(x);
                sortedHoursList.add(y);
            }

            offerList.clear();
            segmentHoursListCurrent.clear();
            loadDifferenceList.clear();
            capacityList.clear();

            for (int i = 1; i <= loadList.size(); i++) {

                segmentIDHydro = i;
                segmentHoursListCurrent.add(sortedHoursList.get(i - 1));
                if (segmentIDHydro == 1) {

                    loadOfSegment = loadList.get(i - 1);
                    segmentLoadOld = loadOfSegment;

                    continue;

                } else {
                    int y = i - 1;
                    loadOfSegment = loadList.get(y);
                    segmentLoadNew = loadOfSegment;
                    loadDifference = segmentLoadOld - segmentLoadNew;
                    remainingLoadDifference = loadDifference;
                    segmentLoadOld = loadOfSegment;
                    loadDifferenceList.add(loadDifference);

                }

                energyDeliveredPrevious = 0;
                for (int q = 0; q < capacityList.size(); q++) {
                    energyDeliveredPrevious = energyDeliveredPrevious + segmentHoursListCurrent.get(q)
                            * capacityList.get(q);
                }

                sumLoadDifference = 0;
                for (int j = 0; j < loadDifferenceList.size(); j++) {
                    sumLoadDifference = sumLoadDifference + loadDifferenceList.get(j);
                    totalCapacity = loadDifferenceList.get(0);

                }

                // if (sumList == 0) {
                // totalCapacity = segmentHoursListCurrent.get(0) +
                // segmentHoursListCurrent.get(1);
                // return totalCapacity;
                // }

                if (sumLoadDifference <= getActualNominalCapacity()) {
                    if (capacityList.size() == 0) {
                        capacityList.add(loadDifference);
                    } else {

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity + loadDifference;
                            capacityList.set(l, declareNewCapacity);
                        }
                        capacityList.add(loadDifference);
                    }

                    energyDeliveredCapacityList = 0;
                    for (int q = 0; q < capacityList.size(); q++) {
                        energyDeliveredCapacityList = capacityList.get(q) * segmentHoursListCurrent.get(q)
                                + energyDeliveredCapacityList;
                    }
                    energyDeliveredOfferList = 0;
                    for (int t = 0; t < offerList.size(); t++) {
                        energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                * offerList.get(t);
                    }

                    energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                    if (energyDeliveredTotal <= energyConstraint) {
                        continue;
                    } else {

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity - loadDifference;
                            capacityList.set(l, declareNewCapacity);
                        }

                        energyDeliveredCapacityList = 0;
                        for (int u = 0; u < capacityList.size(); u++) {
                            energyDeliveredCapacityList += capacityList.get(u) * segmentHoursListCurrent.get(u);
                        }

                        energyDeliveredOfferList = 0;
                        for (int t = 0; t < offerList.size(); t++) {
                            energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                    * offerList.get(t);
                        }

                        energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                        energyAvailable = energyConstraint - energyDeliveredTotal;

                        totalHoursModel = 0;
                        for (int s = 1; s < segmentHoursListCurrent.size(); s++) {
                            totalHoursModel = totalHoursModel + segmentHoursListCurrent.get(s - 1);
                        }

                        availableCapacity = energyAvailable / totalHoursModel;

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                            offerList.add(declareNewCapacity);
                        }

                        if (offerList.size() >= segmentID) {
                            return offerList.get((int) (segmentID - 1));

                        } else {
                            return 0;
                        }

                    }
                } else {

                    if (loadDifferenceList.size() == 1) {

                        availableCapacity = getActualNominalCapacity();

                        energyDeliveredCapacityList = availableCapacity * segmentHoursListCurrent.get(0);

                        energyDeliveredOfferList = 0;
                        for (int t = 0; t < offerList.size(); t++) {
                            energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                    * offerList.get(t);
                        }

                        energyDeliveredTotal = energyDeliveredOfferList + energyDeliveredCapacityList;

                        if (energyDeliveredTotal <= energyConstraint) {
                            fullHours = segmentHoursListCurrent.get(0);
                            segmentHoursListOffered.add(fullHours);
                            offerList.add(availableCapacity);
                            segmentHoursListCurrent.remove(0);
                            loadDifferenceList.remove(0);

                            continue;
                        } else {
                            energyDeliveredTotal = energyDeliveredOfferList;
                            energyAvailable = energyConstraint - energyDeliveredTotal;

                            totalHoursModel = segmentHoursListCurrent.get(0);
                            availableCapacity = energyAvailable / totalHoursModel;

                            offerList.add(availableCapacity);

                            if (offerList.size() >= segmentID) {
                                return offerList.get((int) (segmentID - 1));

                            } else {
                                return 0;
                            }

                        }
                    } else {

                        availableCapacity = getActualNominalCapacity() - capacityList.get(0);

                        remainingLoadDifference = loadDifference - availableCapacity;
                        sumLoadDifference = sumLoadDifference - loadDifferenceList.get(0);

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                            capacityList.set(l, declareNewCapacity);
                        }
                        capacityList.add(availableCapacity);

                        capacityConstrained = true;

                        while (capacityConstrained = true) {
                            energyDeliveredCapacityList = 0;
                            for (int q = 0; q < capacityList.size(); q++) {
                                energyDeliveredCapacityList = capacityList.get(q) * segmentHoursListCurrent.get(q)
                                        + energyDeliveredCapacityList;
                            }
                            energyDeliveredOfferList = 0;
                            for (int t = 0; t < offerList.size(); t++) {
                                energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                        * offerList.get(t);
                            }

                            energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                            if (energyDeliveredTotal <= energyConstraint) {
                                loadDifferenceList.remove(0);
                                fullCapacity = capacityList.get(0);
                                fullHours = segmentHoursListCurrent.get(0);
                                segmentHoursListOffered.add(fullHours);
                                offerList.add(fullCapacity);
                                capacityList.remove(0);
                                segmentHoursListCurrent.remove(0);

                                if (loadDifferenceList.size() == 1) {

                                    if (remainingLoadDifference + capacityList.get(0) < getActualNominalCapacity()) {

                                        // /// VERIFIED
                                        availableCapacity = remainingLoadDifference;
                                        energyDeliveredCapacityList = (availableCapacity + capacityList.get(0))
                                                * segmentHoursListCurrent.get(0);

                                        energyDeliveredOfferList = 0;
                                        for (int t = 0; t < offerList.size(); t++) {
                                            energyDeliveredOfferList = energyDeliveredOfferList
                                                    + segmentHoursListOffered.get(t) * offerList.get(t);
                                        }

                                        energyDeliveredTotal = energyDeliveredOfferList + energyDeliveredCapacityList;

                                        if (energyDeliveredTotal <= energyConstraint) {
                                            obtainOldCapacity = capacityList.get(0);
                                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                                            capacityList.set(0, declareNewCapacity);
                                            break;
                                            // / NOT YET DONE

                                        } else {
                                            energyDeliveredCapacityList = (capacityList.get(0) * segmentHoursListCurrent
                                                    .get(0));

                                            energyDeliveredOfferList = 0;
                                            for (int t = 0; t < offerList.size(); t++) {
                                                energyDeliveredOfferList = energyDeliveredOfferList
                                                        + segmentHoursListOffered.get(t) * offerList.get(t);
                                            }

                                            energyDeliveredTotal = energyDeliveredCapacityList
                                                    + energyDeliveredOfferList;
                                            energyAvailable = energyConstraint - energyDeliveredTotal;

                                            totalHoursModel = segmentHoursListCurrent.get(0);
                                            availableCapacity = energyAvailable / totalHoursModel;
                                            totalCapacity = availableCapacity + capacityList.get(0);
                                            offerList.add(totalCapacity);

                                            if (offerList.size() >= segmentID) {
                                                return offerList.get((int) (segmentID - 1));

                                            } else {
                                                return 0;
                                            }
                                            // DONE

                                        }

                                    } else {
                                        availableCapacity = getActualNominalCapacity();

                                    }

                                    energyDeliveredCapacityList = availableCapacity * segmentHoursListCurrent.get(0);

                                    energyDeliveredOfferList = 0;
                                    for (int t = 0; t < offerList.size(); t++) {
                                        energyDeliveredOfferList = energyDeliveredOfferList
                                                + segmentHoursListOffered.get(t) * offerList.get(t);
                                    }

                                    energyDeliveredTotal = energyDeliveredOfferList + energyDeliveredCapacityList;

                                    if (energyDeliveredTotal <= energyConstraint) {
                                        loadDifferenceList.remove(0);
                                        fullHours = segmentHoursListCurrent.get(0);
                                        segmentHoursListOffered.add(fullHours);
                                        offerList.add(availableCapacity);
                                        segmentHoursListCurrent.remove(0);
                                        capacityList.clear();
                                        break;
                                        // / NOT YET DONE

                                    } else {
                                        energyDeliveredCapacityList = 0;

                                        energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;
                                        energyAvailable = energyConstraint - energyDeliveredTotal;

                                        totalHoursModel = segmentHoursListCurrent.get(0);
                                        availableCapacity = energyAvailable / totalHoursModel;
                                        totalCapacity = availableCapacity;
                                        offerList.add(totalCapacity);

                                        if (offerList.size() >= segmentID) {
                                            return offerList.get((int) (segmentID - 1));

                                        } else {
                                            return 0;
                                        }
                                        // DONE

                                    }

                                } else {
                                    if (sumLoadDifference <= getActualNominalCapacity()) {
                                        // /// VERIFIED DONE
                                        for (int l = 0; l < capacityList.size(); l++) {
                                            obtainOldCapacity = capacityList.get(l);
                                            declareNewCapacity = obtainOldCapacity + remainingLoadDifference;
                                            capacityList.set(l, declareNewCapacity);
                                        }

                                        energyDeliveredCapacityList = 0;
                                        for (int q = 0; q < capacityList.size(); q++) {
                                            energyDeliveredCapacityList = capacityList.get(q)
                                                    * segmentHoursListCurrent.get(q) + energyDeliveredCapacityList;
                                        }
                                        energyDeliveredOfferList = 0;
                                        for (int t = 0; t < offerList.size(); t++) {
                                            energyDeliveredOfferList = energyDeliveredOfferList
                                                    + segmentHoursListOffered.get(t) * offerList.get(t);
                                        }

                                        energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                                        if (energyDeliveredTotal <= energyConstraint) {
                                            break;
                                        } else {
                                            for (int l = 0; l < capacityList.size(); l++) {
                                                obtainOldCapacity = capacityList.get(l);
                                                declareNewCapacity = obtainOldCapacity - remainingLoadDifference;
                                                capacityList.set(l, declareNewCapacity);
                                            }

                                            energyDeliveredCapacityList = 0;
                                            for (int q = 0; q < capacityList.size(); q++) {
                                                energyDeliveredCapacityList = capacityList.get(q)
                                                        * segmentHoursListCurrent.get(q) + energyDeliveredCapacityList;
                                            }

                                            energyDeliveredTotal = energyDeliveredCapacityList
                                                    + energyDeliveredOfferList;

                                            energyAvailable = energyConstraint - energyDeliveredTotal;
                                            totalHoursModel = 0;
                                            for (int s = 1; s < segmentHoursListCurrent.size(); s++) {
                                                totalHoursModel = totalHoursModel + segmentHoursListCurrent.get(s - 1);
                                            }

                                            availableCapacity = energyAvailable / totalHoursModel;

                                            for (int l = 0; l < capacityList.size(); l++) {
                                                obtainOldCapacity = capacityList.get(l);
                                                declareNewCapacity = obtainOldCapacity + availableCapacity;
                                                offerList.add(declareNewCapacity);
                                            }

                                            if (offerList.size() >= segmentID) {
                                                return offerList.get((int) (segmentID - 1));
                                            } else {
                                                return 0;
                                            }
                                            // / DONE
                                        }

                                    } else {
                                        // VERIFIED
                                        availableCapacity = getActualNominalCapacity() - capacityList.get(0);
                                        remainingLoadDifference = remainingLoadDifference - availableCapacity;

                                        sumLoadDifference = sumLoadDifference - loadDifferenceList.get(0);

                                        for (int l = 0; l < capacityList.size(); l++) {
                                            obtainOldCapacity = capacityList.get(l);
                                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                                            capacityList.set(l, declareNewCapacity);
                                        }
                                        capacityConstrained = true;
                                        ;

                                    }

                                }

                            } else {
                                // /VERIFIED

                                for (int l = 0; l < capacityList.size(); l++) {
                                    obtainOldCapacity = capacityList.get(l);
                                    declareNewCapacity = obtainOldCapacity - availableCapacity;
                                    // Calculate capacity
                                }

                                energyDeliveredCapacityList = 0;
                                for (int q = 0; q < capacityList.size(); q++) {
                                    energyDeliveredCapacityList = capacityList.get(q) * segmentHoursListCurrent.get(q)
                                            + energyDeliveredCapacityList;
                                }
                                energyDeliveredOfferList = 0;
                                for (int t = 0; t < offerList.size(); t++) {
                                    energyDeliveredOfferList = energyDeliveredOfferList
                                            + segmentHoursListOffered.get(t) * offerList.get(t);
                                }

                                energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                                energyAvailable = energyConstraint - energyDeliveredTotal;

                                totalHoursModel = 0;
                                for (int s = 1; s < segmentHoursListCurrent.size(); s++) {
                                    totalHoursModel = totalHoursModel + segmentHoursListCurrent.get(s - 1);
                                }

                                availableCapacity = energyAvailable / totalHoursModel;

                                for (int l = 0; l < capacityList.size(); l++) {
                                    obtainOldCapacity = capacityList.get(l);
                                    declareNewCapacity = obtainOldCapacity + availableCapacity;
                                    offerList.add(declareNewCapacity);
                                }

                                if (offerList.size() >= segmentID) {
                                    return offerList.get((int) (segmentID - 1));

                                } else {
                                    return 0;
                                }

                            }
                        }
                    }
                }
            }

            if (capacityList.size() > 0) {
                for (int c = 0; c < capacityList.size(); c++) {
                    fullCapacity = capacityList.get(c);
                    offerList.add(fullCapacity);

                }
            }

            HashMap<Integer, Double> segmentOffer = new HashMap<Integer, Double>();

            for (int g = 1; g <= offerList.size(); g++) {
                segmentOffer.put(g, offerList.get(g - 1));
            }

            if (offerList.size() >= segmentID) {
                return offerList.get((int) (segmentID - 1));

            } else {
                return 0;
            }

        } else {
            return 0;
        }

    }

    public double getExpectedAvailableHydroPowerCapacity(long time, long currentTick, Segment segment,
            long numberOfSegments, ElectricitySpotMarket market, double intermittentBase, double intermittentPeak,
            double intermittentTotal, double energyConstraint, HashMap<Double, Double> interconnectorA,
            HashMap<Double, Double> interconnectorB, double demandFactor) {
        double segmentID = segment.getSegmentID();
        double sumLoadDifference = 0;
        double energyDeliveredPrevious;
        double energyAvailable;
        double totalHoursModel;
        double segmentLoadOld = 0;
        double segmentLoadNew;
        double loadDifference = 0;
        double remainingLoadDifference;
        double fullHours;
        double loadOfSegment;
        double segmentIDHydro = 0;
        double segmentIDHydroLoad;
        double obtainOldCapacity = 0;
        double availableCapacity = 0;
        double declareNewCapacity = 0;
        double baseLoad;
        double totalCapacity = 0;
        double totalHoursList = 0;
        double interconnectorFlow = 0;
        double fullCapacity;
        double energyDeliveredOfferList = 0;
        double energyDeliveredCapacityList;
        double energyDeliveredTotal = 0;
        double residualLoad = 0;
        boolean capacityConstrained;

        double factor = 0;
        List<Double> loadList = new ArrayList<Double>();
        List<Double> hoursList = new ArrayList<Double>();
        List<Double> sortedHoursList = new ArrayList<Double>();
        List<Double> segmentHoursListCurrent = new ArrayList<Double>();
        List<Double> segmentHoursListOffered = new ArrayList<Double>();
        List<Double> loadDifferenceList = new ArrayList<Double>();
        List<Double> capacityList = new ArrayList<Double>();
        List<Double> offerList = new ArrayList<Double>();

        if (isOperational(time)) {

            loadList.clear();

            for (SegmentLoad segmentload : market.getLoadDurationCurve()) {
                Segment segmentHydroLoad = segmentload.getSegment();
                baseLoad = segmentload.getBaseLoad() * demandFactor;
                segmentIDHydroLoad = segmentHydroLoad.getSegmentID();

                // boolean forecast = false;
                if (marketRepository.countAllElectricitySpotMarkets() == 2) {

                    if (currentTick >= 1) {
                        // SegmentClearingPoint scp =
                        // segmentClearingPointRepository
                        // .findOneSegmentClearingPointForMarketSegmentAndTime(currentTick
                        // - 1, segmentHydroLoad,
                        // market, false);

                        // interconnectorFlow = scp.getInterconnectorFlow();

                        if (market.getZone().getName().equals("Country A")) {
                            interconnectorFlow = -1 * interconnectorA.get(segmentID);
                        } else if (market.getZone().getName().equals("Country B")) {
                            interconnectorFlow = -1 * interconnectorB.get(segmentID);
                        }

                    } else {
                        if (market.getZone().getName().equals("Country A")) {
                            interconnectorFlow = -2000;
                        } else if (market.getZone().getName().equals("Country B")) {
                            interconnectorFlow = 2000;
                        }
                    }

                }
                if (segmentIDHydroLoad != numberOfSegments) {

                    double segmentPortionHydro = (numberOfSegments - segmentIDHydroLoad) / (numberOfSegments - 1);
                    double range = intermittentBase - intermittentPeak;

                    factor = intermittentBase - segmentPortionHydro * range;
                    residualLoad = baseLoad - factor * intermittentTotal + interconnectorFlow;
                    if (residualLoad > 0) {
                        loadList.add(residualLoad);
                        hoursList.add(segmentHydroLoad.getLengthInHours());
                    } else {
                        continue;
                    }

                } else {
                    if (residualLoad > 0) {
                        factor = intermittentBase;
                        residualLoad = baseLoad - factor * intermittentTotal + interconnectorFlow;
                        loadList.add(residualLoad);
                        hoursList.add(segmentHydroLoad.getLengthInHours());
                    } else {
                        continue;
                    }

                }

            }

            double count = 0;
            loadList.add(count);
            hoursList.add(count);

            HashMap<Double, Double> sorting = new HashMap<Double, Double>();

            for (int g = 0; g < loadList.size(); g++) {
                sorting.put(loadList.get(g), hoursList.get(g));
            }

            Collections.sort(loadList, Collections.reverseOrder());

            for (int h = 0; h < loadList.size(); h++) {
                double x = loadList.get(h);
                double y = sorting.get(x);
                sortedHoursList.add(y);
            }

            offerList.clear();
            segmentHoursListCurrent.clear();
            loadDifferenceList.clear();
            capacityList.clear();

            for (int i = 1; i <= loadList.size(); i++) {

                segmentIDHydro = i;
                segmentHoursListCurrent.add(sortedHoursList.get(i - 1));
                if (segmentIDHydro == 1) {

                    loadOfSegment = loadList.get(i - 1);
                    segmentLoadOld = loadOfSegment;

                    continue;

                } else {
                    int y = i - 1;
                    loadOfSegment = loadList.get(y);
                    segmentLoadNew = loadOfSegment;
                    loadDifference = segmentLoadOld - segmentLoadNew;
                    remainingLoadDifference = loadDifference;
                    segmentLoadOld = loadOfSegment;
                    loadDifferenceList.add(loadDifference);

                }

                energyDeliveredPrevious = 0;
                for (int q = 0; q < capacityList.size(); q++) {
                    energyDeliveredPrevious = energyDeliveredPrevious + segmentHoursListCurrent.get(q)
                            * capacityList.get(q);
                }

                sumLoadDifference = 0;
                for (int j = 0; j < loadDifferenceList.size(); j++) {
                    sumLoadDifference = sumLoadDifference + loadDifferenceList.get(j);
                    totalCapacity = loadDifferenceList.get(0);

                }

                // if (sumList == 0) {
                // totalCapacity = segmentHoursListCurrent.get(0) +
                // segmentHoursListCurrent.get(1);
                // return totalCapacity;
                // }

                if (sumLoadDifference <= getActualNominalCapacity()) {
                    if (capacityList.size() == 0) {
                        capacityList.add(loadDifference);
                    } else {

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity + loadDifference;
                            capacityList.set(l, declareNewCapacity);
                        }
                        capacityList.add(loadDifference);
                    }

                    energyDeliveredCapacityList = 0;
                    for (int q = 0; q < capacityList.size(); q++) {
                        energyDeliveredCapacityList = capacityList.get(q) * segmentHoursListCurrent.get(q)
                                + energyDeliveredCapacityList;
                    }
                    energyDeliveredOfferList = 0;
                    for (int t = 0; t < offerList.size(); t++) {
                        energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                * offerList.get(t);
                    }

                    energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                    if (energyDeliveredTotal <= energyConstraint) {
                        continue;
                    } else {

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity - loadDifference;
                            capacityList.set(l, declareNewCapacity);
                        }

                        energyDeliveredCapacityList = 0;
                        for (int u = 0; u < capacityList.size(); u++) {
                            energyDeliveredCapacityList += capacityList.get(u) * segmentHoursListCurrent.get(u);
                        }

                        energyDeliveredOfferList = 0;
                        for (int t = 0; t < offerList.size(); t++) {
                            energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                    * offerList.get(t);
                        }

                        energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                        energyAvailable = energyConstraint - energyDeliveredTotal;

                        totalHoursModel = 0;
                        for (int s = 1; s < segmentHoursListCurrent.size(); s++) {
                            totalHoursModel = totalHoursModel + segmentHoursListCurrent.get(s - 1);
                        }

                        availableCapacity = energyAvailable / totalHoursModel;

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                            offerList.add(declareNewCapacity);
                        }

                        if (offerList.size() >= segmentID) {
                            return offerList.get((int) (segmentID - 1));

                        } else {
                            return 0;
                        }

                    }
                } else {

                    if (loadDifferenceList.size() == 1) {

                        availableCapacity = getActualNominalCapacity();

                        energyDeliveredCapacityList = availableCapacity * segmentHoursListCurrent.get(0);

                        energyDeliveredOfferList = 0;
                        for (int t = 0; t < offerList.size(); t++) {
                            energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                    * offerList.get(t);
                        }

                        energyDeliveredTotal = energyDeliveredOfferList + energyDeliveredCapacityList;

                        if (energyDeliveredTotal <= energyConstraint) {
                            fullHours = segmentHoursListCurrent.get(0);
                            segmentHoursListOffered.add(fullHours);
                            offerList.add(availableCapacity);
                            segmentHoursListCurrent.remove(0);
                            loadDifferenceList.remove(0);

                            continue;
                        } else {
                            energyDeliveredTotal = energyDeliveredOfferList;
                            energyAvailable = energyConstraint - energyDeliveredTotal;

                            totalHoursModel = segmentHoursListCurrent.get(0);
                            availableCapacity = energyAvailable / totalHoursModel;

                            offerList.add(availableCapacity);

                            if (offerList.size() >= segmentID) {
                                return offerList.get((int) (segmentID - 1));

                            } else {
                                return 0;
                            }

                        }
                    } else {

                        availableCapacity = getActualNominalCapacity() - capacityList.get(0);

                        remainingLoadDifference = loadDifference - availableCapacity;
                        sumLoadDifference = sumLoadDifference - loadDifferenceList.get(0);

                        for (int l = 0; l < capacityList.size(); l++) {
                            obtainOldCapacity = capacityList.get(l);
                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                            capacityList.set(l, declareNewCapacity);
                        }
                        capacityList.add(availableCapacity);

                        capacityConstrained = true;

                        while (capacityConstrained = true) {
                            energyDeliveredCapacityList = 0;
                            for (int q = 0; q < capacityList.size(); q++) {
                                energyDeliveredCapacityList = capacityList.get(q) * segmentHoursListCurrent.get(q)
                                        + energyDeliveredCapacityList;
                            }
                            energyDeliveredOfferList = 0;
                            for (int t = 0; t < offerList.size(); t++) {
                                energyDeliveredOfferList = energyDeliveredOfferList + segmentHoursListOffered.get(t)
                                        * offerList.get(t);
                            }

                            energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                            if (energyDeliveredTotal <= energyConstraint) {
                                loadDifferenceList.remove(0);
                                fullCapacity = capacityList.get(0);
                                fullHours = segmentHoursListCurrent.get(0);
                                segmentHoursListOffered.add(fullHours);
                                offerList.add(fullCapacity);
                                capacityList.remove(0);
                                segmentHoursListCurrent.remove(0);

                                if (loadDifferenceList.size() == 1) {

                                    if (remainingLoadDifference + capacityList.get(0) < getActualNominalCapacity()) {

                                        // /// VERIFIED
                                        availableCapacity = remainingLoadDifference;
                                        energyDeliveredCapacityList = (availableCapacity + capacityList.get(0))
                                                * segmentHoursListCurrent.get(0);

                                        energyDeliveredOfferList = 0;
                                        for (int t = 0; t < offerList.size(); t++) {
                                            energyDeliveredOfferList = energyDeliveredOfferList
                                                    + segmentHoursListOffered.get(t) * offerList.get(t);
                                        }

                                        energyDeliveredTotal = energyDeliveredOfferList + energyDeliveredCapacityList;

                                        if (energyDeliveredTotal <= energyConstraint) {
                                            obtainOldCapacity = capacityList.get(0);
                                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                                            capacityList.set(0, declareNewCapacity);
                                            break;
                                            // / NOT YET DONE

                                        } else {
                                            energyDeliveredCapacityList = (capacityList.get(0) * segmentHoursListCurrent
                                                    .get(0));

                                            energyDeliveredOfferList = 0;
                                            for (int t = 0; t < offerList.size(); t++) {
                                                energyDeliveredOfferList = energyDeliveredOfferList
                                                        + segmentHoursListOffered.get(t) * offerList.get(t);
                                            }

                                            energyDeliveredTotal = energyDeliveredCapacityList
                                                    + energyDeliveredOfferList;
                                            energyAvailable = energyConstraint - energyDeliveredTotal;

                                            totalHoursModel = segmentHoursListCurrent.get(0);
                                            availableCapacity = energyAvailable / totalHoursModel;
                                            totalCapacity = availableCapacity + capacityList.get(0);
                                            offerList.add(totalCapacity);

                                            if (offerList.size() >= segmentID) {
                                                return offerList.get((int) (segmentID - 1));

                                            } else {
                                                return 0;
                                            }
                                            // DONE

                                        }

                                    } else {
                                        availableCapacity = getActualNominalCapacity();

                                    }

                                    energyDeliveredCapacityList = availableCapacity * segmentHoursListCurrent.get(0);

                                    energyDeliveredOfferList = 0;
                                    for (int t = 0; t < offerList.size(); t++) {
                                        energyDeliveredOfferList = energyDeliveredOfferList
                                                + segmentHoursListOffered.get(t) * offerList.get(t);
                                    }

                                    energyDeliveredTotal = energyDeliveredOfferList + energyDeliveredCapacityList;

                                    if (energyDeliveredTotal <= energyConstraint) {
                                        loadDifferenceList.remove(0);
                                        fullHours = segmentHoursListCurrent.get(0);
                                        segmentHoursListOffered.add(fullHours);
                                        offerList.add(availableCapacity);
                                        segmentHoursListCurrent.remove(0);
                                        capacityList.clear();
                                        break;
                                        // / NOT YET DONE

                                    } else {
                                        energyDeliveredCapacityList = 0;

                                        energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;
                                        energyAvailable = energyConstraint - energyDeliveredTotal;

                                        totalHoursModel = segmentHoursListCurrent.get(0);
                                        availableCapacity = energyAvailable / totalHoursModel;
                                        totalCapacity = availableCapacity;
                                        offerList.add(totalCapacity);

                                        if (offerList.size() >= segmentID) {
                                            return offerList.get((int) (segmentID - 1));

                                        } else {
                                            return 0;
                                        }
                                        // DONE

                                    }

                                } else {
                                    if (sumLoadDifference <= getActualNominalCapacity()) {
                                        // /// VERIFIED DONE
                                        for (int l = 0; l < capacityList.size(); l++) {
                                            obtainOldCapacity = capacityList.get(l);
                                            declareNewCapacity = obtainOldCapacity + remainingLoadDifference;
                                            capacityList.set(l, declareNewCapacity);
                                        }

                                        energyDeliveredCapacityList = 0;
                                        for (int q = 0; q < capacityList.size(); q++) {
                                            energyDeliveredCapacityList = capacityList.get(q)
                                                    * segmentHoursListCurrent.get(q) + energyDeliveredCapacityList;
                                        }
                                        energyDeliveredOfferList = 0;
                                        for (int t = 0; t < offerList.size(); t++) {
                                            energyDeliveredOfferList = energyDeliveredOfferList
                                                    + segmentHoursListOffered.get(t) * offerList.get(t);
                                        }

                                        energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                                        if (energyDeliveredTotal <= energyConstraint) {
                                            break;
                                        } else {
                                            for (int l = 0; l < capacityList.size(); l++) {
                                                obtainOldCapacity = capacityList.get(l);
                                                declareNewCapacity = obtainOldCapacity - remainingLoadDifference;
                                                capacityList.set(l, declareNewCapacity);
                                            }

                                            energyDeliveredCapacityList = 0;
                                            for (int q = 0; q < capacityList.size(); q++) {
                                                energyDeliveredCapacityList = capacityList.get(q)
                                                        * segmentHoursListCurrent.get(q) + energyDeliveredCapacityList;
                                            }

                                            energyDeliveredTotal = energyDeliveredCapacityList
                                                    + energyDeliveredOfferList;

                                            energyAvailable = energyConstraint - energyDeliveredTotal;
                                            totalHoursModel = 0;
                                            for (int s = 1; s < segmentHoursListCurrent.size(); s++) {
                                                totalHoursModel = totalHoursModel + segmentHoursListCurrent.get(s - 1);
                                            }

                                            availableCapacity = energyAvailable / totalHoursModel;

                                            for (int l = 0; l < capacityList.size(); l++) {
                                                obtainOldCapacity = capacityList.get(l);
                                                declareNewCapacity = obtainOldCapacity + availableCapacity;
                                                offerList.add(declareNewCapacity);
                                            }

                                            if (offerList.size() >= segmentID) {
                                                return offerList.get((int) (segmentID - 1));
                                            } else {
                                                return 0;
                                            }
                                            // / DONE
                                        }

                                    } else {
                                        // VERIFIED
                                        availableCapacity = getActualNominalCapacity() - capacityList.get(0);
                                        remainingLoadDifference = remainingLoadDifference - availableCapacity;

                                        sumLoadDifference = sumLoadDifference - loadDifferenceList.get(0);

                                        for (int l = 0; l < capacityList.size(); l++) {
                                            obtainOldCapacity = capacityList.get(l);
                                            declareNewCapacity = obtainOldCapacity + availableCapacity;
                                            capacityList.set(l, declareNewCapacity);
                                        }
                                        capacityConstrained = true;
                                        ;

                                    }

                                }

                            } else {
                                // /VERIFIED

                                for (int l = 0; l < capacityList.size(); l++) {
                                    obtainOldCapacity = capacityList.get(l);
                                    declareNewCapacity = obtainOldCapacity - availableCapacity;
                                    // Calculate capacity
                                }

                                energyDeliveredCapacityList = 0;
                                for (int q = 0; q < capacityList.size(); q++) {
                                    energyDeliveredCapacityList = capacityList.get(q) * segmentHoursListCurrent.get(q)
                                            + energyDeliveredCapacityList;
                                }
                                energyDeliveredOfferList = 0;
                                for (int t = 0; t < offerList.size(); t++) {
                                    energyDeliveredOfferList = energyDeliveredOfferList
                                            + segmentHoursListOffered.get(t) * offerList.get(t);
                                }

                                energyDeliveredTotal = energyDeliveredCapacityList + energyDeliveredOfferList;

                                energyAvailable = energyConstraint - energyDeliveredTotal;

                                totalHoursModel = 0;
                                for (int s = 1; s < segmentHoursListCurrent.size(); s++) {
                                    totalHoursModel = totalHoursModel + segmentHoursListCurrent.get(s - 1);
                                }

                                availableCapacity = energyAvailable / totalHoursModel;

                                for (int l = 0; l < capacityList.size(); l++) {
                                    obtainOldCapacity = capacityList.get(l);
                                    declareNewCapacity = obtainOldCapacity + availableCapacity;
                                    offerList.add(declareNewCapacity);
                                }

                                if (offerList.size() >= segmentID) {
                                    return offerList.get((int) (segmentID - 1));

                                } else {
                                    return 0;
                                }

                            }
                        }
                    }
                }
            }

            if (capacityList.size() > 0) {
                for (int c = 0; c < capacityList.size(); c++) {
                    fullCapacity = capacityList.get(c);
                    offerList.add(fullCapacity);

                }
            }

            HashMap<Integer, Double> segmentOffer = new HashMap<Integer, Double>();

            for (int g = 1; g <= offerList.size(); g++) {
                segmentOffer.put(g, offerList.get(g - 1));
            }

            if (offerList.size() >= segmentID) {
                return offerList.get((int) (segmentID - 1));

            } else {
                return 0;
            }

        } else {
            return 0;
        }

    }

}
