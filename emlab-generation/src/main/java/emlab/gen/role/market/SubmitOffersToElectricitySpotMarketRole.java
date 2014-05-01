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
package emlab.gen.role.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.transaction.annotation.Transactional;

import agentspring.role.Role;
import agentspring.role.RoleComponent;
import emlab.gen.domain.agent.DecarbonizationModel;
import emlab.gen.domain.agent.EnergyProducer;
import emlab.gen.domain.agent.Government;
import emlab.gen.domain.market.Bid;
import emlab.gen.domain.market.electricity.ElectricitySpotMarket;
import emlab.gen.domain.market.electricity.PowerPlantDispatchPlan;
import emlab.gen.domain.market.electricity.Segment;
import emlab.gen.domain.market.electricity.SegmentClearingPoint;
import emlab.gen.domain.market.electricity.SegmentLoad;
import emlab.gen.domain.technology.HydroPowerPlant;
import emlab.gen.domain.technology.Interconnector;
import emlab.gen.domain.technology.PowerGeneratingTechnology;
import emlab.gen.domain.technology.PowerPlant;
import emlab.gen.domain.technology.Substance;
import emlab.gen.domain.technology.SubstanceShareInFuelMix;
import emlab.gen.repository.Reps;
import emlab.gen.role.AbstractEnergyProducerRole;

/**
 * {@link EnergyProducer} submits offers to the {@link ElectricitySpotMarket}.
 * One {@link Bid} per {@link PowerPlant}.
 * 
 * @author <a href="mailto:A.Chmieliauskas@tudelft.nl">Alfredas
 *         Chmieliauskas</a> @author <a
 *         href="mailto:E.J.L.Chappin@tudelft.nl">Emile Chappin</a>
 * 
 */
@RoleComponent
public class SubmitOffersToElectricitySpotMarketRole extends AbstractEnergyProducerRole<EnergyProducer> implements
        Role<EnergyProducer> {

    @Autowired
    Neo4jTemplate template;

    @Autowired
    Reps reps;

    @Override
    public void act(EnergyProducer producer) {

        createOffersForElectricitySpotMarket(producer, getCurrentTick(), false, null);
    }

    @Transactional
    public List<PowerPlantDispatchPlan> createOffersForElectricitySpotMarket(EnergyProducer producer, long tick,
            boolean forecast, Map<Substance, Double> forecastedFuelPrices) {
        List<PowerPlantDispatchPlan> ppdpList = new ArrayList<PowerPlantDispatchPlan>();

        if (forecastedFuelPrices == null && !forecast) {
            DecarbonizationModel model = reps.genericRepository.findFirst(DecarbonizationModel.class);
            forecastedFuelPrices = new HashMap<Substance, Double>();
            for (Substance substance : reps.substanceRepository.findAllSubstancesTradedOnCommodityMarkets()) {
                forecastedFuelPrices.put(substance, findLastKnownPriceForSubstance(substance, getCurrentTick()));
            }
        }

        double windBase = 0;
        double windPeak = 0;
        double totalWindNominal = 0;
        double windOffshoreBase = 0;
        double windOffshorePeak = 0;
        double totalWindOffshoreNominal = 0;
        double solarBase = 0;
        double solarPeak = 0;
        double totalSolarNominal = 0;
        double intermittentBase = 0;
        double intermittentBaseSolar = 0;
        double intermittentBaseWind = 0;
        double intermittentBaseWindOffshore = 0;
        double intermittentPeakSolar = 0;
        double intermittentPeakWind = 0;
        double intermittentPeakWindOffshore = 0;
        double intermittentPeak = 0;
        double intermittentTotal = 0;
        double energyConstraint = 0;
        HashMap<Double, Double> interconnectorA = new HashMap<Double, Double>();
        HashMap<Double, Double> interconnectorB = new HashMap<Double, Double>();

        long numberOfSegments = reps.segmentRepository.count();

        ElectricitySpotMarket market = null;
        if (producer != null) {
            market = producer.getInvestorMarket();
        }

        for (PowerPlant plant : reps.powerPlantRepository.findOperationalPowerPlantsAsList(getCurrentTick())) {
            PowerGeneratingTechnology technology = plant.getTechnology();
            String x = technology.getName();
            if (producer == null) {
                break;
            }
            if (x.equals("Wind")) {
                totalWindNominal = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsInMarketAndTechnology(market, technology,
                                getCurrentTick());
                windBase = plant.getTechnology().getBaseSegmentDependentAvailability();
                windPeak = plant.getTechnology().getPeakSegmentDependentAvailability();
                // logger.warn("Ik heb {} MW aan wind", totalWindNominal);

            }
            if (x.equals("Photovoltaic")) {
                totalSolarNominal = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsInMarketAndTechnology(market, technology,
                                getCurrentTick());
                solarBase = plant.getTechnology().getBaseSegmentDependentAvailability();
                solarPeak = plant.getTechnology().getPeakSegmentDependentAvailability();
                // logger.warn("Ik heb {} MW aan zon", totalSolarNominal);

            }

            if (x.equals("WindOffshore")) {
                totalWindOffshoreNominal = reps.powerPlantRepository
                        .calculateCapacityOfExpectedOperationalPowerPlantsInMarketAndTechnology(market, technology,
                                getCurrentTick());
                windOffshoreBase = plant.getTechnology().getBaseSegmentDependentAvailability();
                windOffshorePeak = plant.getTechnology().getPeakSegmentDependentAvailability();
                // logger.warn("Ik heb {} MW aan wind", totalWindNominal);

            }

            if (totalSolarNominal > 0) {
                intermittentBaseSolar = totalSolarNominal
                        / (totalSolarNominal + totalWindNominal + totalWindOffshoreNominal) * solarBase;
            }
            if (totalSolarNominal > 0) {
                intermittentPeakSolar = totalSolarNominal
                        / (totalSolarNominal + totalWindNominal + totalWindOffshoreNominal) * solarPeak;
            }
            if (totalWindNominal > 0) {
                intermittentBaseWind = totalWindNominal
                        / (totalSolarNominal + totalWindNominal + totalWindOffshoreNominal) * windBase;
            }
            if (totalWindNominal > 0) {
                intermittentPeakWind = totalWindNominal
                        / (totalSolarNominal + totalWindNominal + totalWindOffshoreNominal) * windPeak;
            }
            if (totalWindOffshoreNominal > 0) {
                intermittentBaseWindOffshore = totalWindOffshoreNominal
                        / (totalSolarNominal + totalWindNominal + totalWindOffshoreNominal) * windOffshoreBase;
            }
            if (totalWindOffshoreNominal > 0) {
                intermittentPeakWindOffshore = totalWindOffshoreNominal
                        / (totalSolarNominal + totalWindNominal + totalWindOffshoreNominal) * windOffshorePeak;
            }
            intermittentBase = intermittentBaseSolar + intermittentBaseWind + intermittentBaseWindOffshore;
            intermittentPeak = intermittentPeakSolar + intermittentPeakWind + intermittentPeakWindOffshore;
            intermittentTotal = totalSolarNominal + totalWindNominal + totalWindOffshoreNominal;
        }

        if (reps.marketRepository.countAllElectricitySpotMarkets() == 2) {

            if (getCurrentTick() >= 1) {

                for (SegmentLoad segmentload : market.getLoadDurationCurve()) {

                    Interconnector interconnector = template.findAll(Interconnector.class).iterator().next();
                    double interconnectorCapacityPreviousTick = interconnector.getCapacity(getCurrentTick() - 1);
                    double interconnectorCapacityCurrentTick = interconnector.getCapacity(getCurrentTick());

                    if (market.getZone().getName().equals("Country A")) {

                        Segment segment = segmentload.getSegment();
                        double segmentID = segment.getSegmentID();
                        SegmentClearingPoint scp = reps.segmentClearingPointRepository
                                .findOneSegmentClearingPointForMarketSegmentAndTime(getCurrentTick() - 1, segment,
                                        market, false);

                        double interconnectorFlow = scp.getInterconnectorFlow()
                                + (Math.abs(scp.getInterconnectorFlow()) / (scp.getInterconnectorFlow()))
                                * (interconnectorCapacityCurrentTick - interconnectorCapacityPreviousTick);

                        interconnectorA.put(segmentID, interconnectorFlow);

                    } else if (market.getZone().getName().equals("Country B")) {

                        Segment segment = segmentload.getSegment();
                        double segmentID = segment.getSegmentID();
                        SegmentClearingPoint scp = reps.segmentClearingPointRepository
                                .findOneSegmentClearingPointForMarketSegmentAndTime(getCurrentTick() - 1, segment,
                                        market, false);

                        // Interconnector interconnector =
                        // reps.template.findAll(Interconnector.class).iterator().next();

                        // double interconnectorsize =
                        // interconnector.getCapacity(getCurrentTick() - 1);

                        double interconnectorFlow = scp.getInterconnectorFlow()
                                + (Math.abs(scp.getInterconnectorFlow()) / (scp.getInterconnectorFlow()))
                                * (interconnectorCapacityCurrentTick - interconnectorCapacityPreviousTick);

                        interconnectorB.put(segmentID, interconnectorFlow);

                        // Segment segment = segmentload.getSegment();
                        // double segmentID = segment.getSegmentID();
                        // SegmentClearingPoint scp =
                        // reps.segmentClearingPointRepository
                        // .findOneSegmentClearingPointForMarketSegmentAndTime(getCurrentTick()
                        // - 1, segment, market,
                        // false);

                        // double interconnectorFlow =
                        // -scp.getInterconnectorFlow();
                        // interconnector.put(segmentID, interconnectorFlow);

                    }
                }
            }
        } else {
            double help = 0;
            interconnectorA.put(help, help);
            interconnectorB.put(help, help);

        }

        // if (producer != null)
        // market = producer.getInvestorMarket();

        Iterable<PowerPlant> powerPlants;
        if (producer != null) {
            powerPlants = forecast ? reps.powerPlantRepository.findExpectedOperationalPowerPlantsInMarketByOwner(
                    market, tick, producer) : reps.powerPlantRepository.findOperationalPowerPlantsByOwner(producer,
                    tick);
        } else {
            powerPlants = forecast ? reps.powerPlantRepository.findExpectedOperationalPowerPlants(tick)
                    : reps.powerPlantRepository.findOperationalPowerPlants(tick);
        }

        boolean producerIsNull = (producer == null) ? true : false;
        // find all my operating power plants
        for (PowerPlant plant : powerPlants) {

            if (producerIsNull) {
                market = reps.marketRepository.findElectricitySpotMarketForZone(plant.getLocation().getZone());
                producer = plant.getOwner();
            }

            double mc;
            double price;
            if (!forecast) {
                mc = calculateMarginalCostExclCO2MarketCost(plant, tick);
                price = mc * producer.getPriceMarkUp();
            } else {
                mc = calculateExpectedMarginalCostExclCO2MarketCost(plant, forecastedFuelPrices, tick);
                price = mc * producer.getPriceMarkUp();
            }

            logger.info("Submitting offers for {} with technology {}", plant.getName(), plant.getTechnology().getName());

            if (plant instanceof HydroPowerPlant) {
                HydroPowerPlant hydroPlant = (HydroPowerPlant) plant;
                energyConstraint = market.getEnergyConstraintTrend().getEnergyValue();

            }

            for (SegmentLoad segmentload : market.getLoadDurationCurve()) {

                Segment segment = segmentload.getSegment();
                double segmentID = segment.getSegmentID();
                double capacity;
                if (tick == getCurrentTick()) {
                    capacity = plant.getAvailableCapacity(tick, segment, numberOfSegments);
                } else {
                    capacity = plant.getExpectedAvailableCapacity(tick, segment, numberOfSegments);
                }

                if (plant instanceof HydroPowerPlant) {
                    HydroPowerPlant hydroPlant = (HydroPowerPlant) plant;
                    capacity = hydroPlant.getAvailableHydroPowerCapacity(getCurrentTick(), segment, numberOfSegments,
                            market, intermittentBase, intermittentPeak, intermittentTotal, energyConstraint,
                            interconnectorA, interconnectorB);

                    price = 0;
                    logger.warn("I OFFER {} MW HYDROPOWER for segment {} in country {}", capacity, segmentID);

                } else {

                    capacity = plant.getAvailableCapacity(getCurrentTick(), segment, numberOfSegments);
                }

                logger.info("I bid capacity: {} and price: {}", capacity, mc);

                PowerPlantDispatchPlan plan = reps.powerPlantDispatchPlanRepository
                        .findOnePowerPlantDispatchPlanForPowerPlantForSegmentForTime(plant, segment, tick, forecast);
                // TODO: handle exception

                // plan =
                // reps.powerPlantDispatchPlanRepository.findOnePowerPlantDispatchPlanForPowerPlantForSegmentForTime(plant,
                // segment,
                // getCurrentTick());
                // Iterable<PowerPlantDispatchPlan> plans =
                // reps.powerPlantDispatchPlanRepository
                // .findAllPowerPlantDispatchPlanForPowerPlantForSegmentForTime(plant,
                // segment, getCurrentTick());

                if (plan == null) {
                    plan = new PowerPlantDispatchPlan().persist();
                    // plan.specifyNotPersist(plant, producer, market, segment,
                    // time, price, bidWithoutCO2, spotMarketCapacity,
                    // longTermContractCapacity, status);
                    plan.specifyNotPersist(plant, producer, market, segment, tick, price, price, capacity, 0,
                            Bid.SUBMITTED, forecast);
                } else {
                    // plan = plans.iterator().next();
                    plan.setBidder(producer);
                    plan.setBiddingMarket(market);
                    plan.setPrice(mc);
                    plan.setBidWithoutCO2(mc);
                    plan.setAmount(capacity);
                    plan.setAcceptedAmount(0d);
                    plan.setCapacityLongTermContract(0d);
                    plan.setStatus(Bid.SUBMITTED);
                    plan.setForecast(forecast);
                }
                ppdpList.add(plan);

            }

        }

        return ppdpList;
    }

    @Transactional
    void updateMarginalCostInclCO2AfterFuelMixChange(double co2Price,
            Map<ElectricitySpotMarket, Double> nationalMinCo2Prices, long clearingTick, boolean forecast,
            Map<Substance, Double> fuelPriceMap) {

        int i = 0;
        int j = 0;

        Government government = reps.template.findAll(Government.class).iterator().next();
        for (PowerPlantDispatchPlan plan : reps.powerPlantDispatchPlanRepository.findAllPowerPlantDispatchPlansForTime(
                clearingTick, forecast)) {
            j++;

            double effectiveCO2Price;

            double capacity = plan.getAmount();
            if (nationalMinCo2Prices.get(plan.getBiddingMarket()) > co2Price)
                effectiveCO2Price = nationalMinCo2Prices.get(plan.getBiddingMarket());
            else
                effectiveCO2Price = co2Price;

            if (plan.getPowerPlant().getFuelMix().size() > 1) {

                double oldmc = plan.getBidWithoutCO2();

                // Fuels
                Set<Substance> possibleFuels = plan.getPowerPlant().getTechnology().getFuels();
                Map<Substance, Double> substancePriceMap = new HashMap<Substance, Double>();

                for (Substance substance : possibleFuels) {
                    substancePriceMap.put(substance, fuelPriceMap.get(substance));
                }
                Set<SubstanceShareInFuelMix> fuelMix = calculateFuelMix(plan.getPowerPlant(), substancePriceMap,
                        government.getCO2Tax(clearingTick) + effectiveCO2Price);
                plan.getPowerPlant().setFuelMix(fuelMix);
                double mc = calculateMarginalCostExclCO2MarketCost(plan.getPowerPlant(), clearingTick);
                if (mc != oldmc) {
                    plan.setBidWithoutCO2(mc);
                    i++;
                }

            }

            plan.setPrice(plan.getBidWithoutCO2()
                    + (effectiveCO2Price * plan.getPowerPlant().calculateEmissionIntensity()));

            plan.setStatus(Bid.SUBMITTED);
            plan.setAmount(capacity);
            plan.setAcceptedAmount(0d);
            plan.setCapacityLongTermContract(0d);

        }

        // logger.warn("Marginal cost of {} of {} plans changed", i, j);

    }

}
