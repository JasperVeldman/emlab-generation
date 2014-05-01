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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.neo4j.annotation.NodeEntity;

import emlab.gen.domain.market.electricity.ElectricitySpotMarket;
import emlab.gen.domain.market.electricity.Segment;
import emlab.gen.domain.market.electricity.SegmentLoad;
import emlab.gen.repository.MarketRepository;

/**
 * Representation of a power plant
 * 
 * @author jcrichstein
 * @author ejlchappin
 * 
 */

@Configurable
@NodeEntity
public class HydroPowerPlant extends PowerPlant {

    @Autowired
    MarketRepository marketRepository;

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
                        interconnectorFlow = 0;
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
                        interconnectorFlow = 0;
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
