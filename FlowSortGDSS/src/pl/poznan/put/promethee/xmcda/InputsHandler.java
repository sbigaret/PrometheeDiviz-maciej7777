package pl.poznan.put.promethee.xmcda;

import org.xmcda.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Maciej Uniejewski on 2016-12-10.
 */
public class InputsHandler {

    public static class Inputs {
        public List<String> alternativesIds;
        public List<String> categoriesIds;
        public List<String> criteriaIds;
        public Map<String, Integer> categoriesRanking;
        public List<List<String>> profilesIds;
        public List<Map<String, Double>> alternativesFlows;
        public Map<String, Double> alternativesFlowsAverage;
        public List<List<CategoryProfile>> categoryProfiles;
        public ComparisonWithProfiles profilesType;
        public List<Double> decisionMakersWages;
        public Boolean assignToABetterClass;
        public Integer decisionMakers;
        public HashMap<String, String> criteriaPreferencesDirection;
        public List<Map<String, Map<String, Double>>> profilesPerformance;
        public Map<String, Double> profilesFlows;
    }

    public enum ComparisonWithProfiles {
        CENTRAL("central"),
        BOUNDING("bounding");
        private String label;

        ComparisonWithProfiles(String operatorLabel) {
            label = operatorLabel;
        }

        public final String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }

        public static ComparisonWithProfiles fromString(String operatorLabel) {
            if (operatorLabel == null)
                throw new NullPointerException("operatorLabel is null");
            for (ComparisonWithProfiles op : ComparisonWithProfiles.values()) {
                if (op.toString().equals(operatorLabel))
                    return op;
            }
            throw new IllegalArgumentException("No enum ComparisonWithProfiles with label " + operatorLabel);
        }
    }

    static public Inputs checkAndExtractInputs(XMCDA xmcda, ProgramExecutionResult xmcda_exec_results) {
        Inputs inputsDict = checkInputs(xmcda, xmcda_exec_results);
        if (xmcda_exec_results.isError())
            return null;
        return inputsDict;
    }

    protected static Inputs checkInputs(XMCDA xmcda, ProgramExecutionResult errors) {
        Inputs inputs = new Inputs();

        checkAndExtractAlternatives(inputs, xmcda, errors);
        checkAndExtractCategories(inputs, xmcda, errors);
        checkCategoriesRanking(inputs, xmcda, errors);
        checkAndExtractParameters(inputs, xmcda, errors);
        checkAndExtractNumberOfDecisionMakers(inputs, xmcda, errors);
        checkAndExtractProfilesIds(inputs, xmcda, errors);
        checkAndExtractCriteria(inputs, xmcda, errors);
        checkAndExtractCriteriaPreferencesDirection(inputs, xmcda, errors);
        checkAndExtractProfilesPerformance(inputs, xmcda, errors);
        checkDominanceCondition(inputs, errors);
        checkAndExtractAlternativesFlows(inputs, xmcda, errors);
        extractFlowsAverage(inputs);
        checkAndExtractProfilesFlows(inputs, xmcda, errors);

        return inputs;
    }

    protected static void checkAndExtractAlternatives(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        if (xmcda.alternatives.size() == 0) {
            errors.addError("No alternatives list has been supplied");
        } else {
            List<String> alternativesIds = xmcda.alternatives.getActiveAlternatives().stream().filter(a -> a.getMarker().equals("alternatives")).map(
                    Alternative::id).collect(Collectors.toList());
            if (alternativesIds.size() < 1)
                errors.addError("The alternatives list can not be empty");

            inputs.alternativesIds = alternativesIds;
        }
    }

    protected static void checkAndExtractCategories(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        Categories categoriesList = xmcda.categories;
        if (xmcda.categories.size() == 0) {
            errors.addError("No categories list has been supplied");
        } else {
            List<String> categories = xmcda.categories.getActiveCategories().stream().filter(a -> a.getMarker().equals("categories")).map(
                    Category::id).collect(Collectors.toList());
            inputs.categoriesIds = categories;
            if (categories.isEmpty())
                errors.addError("The category list can not be empty");
        }
    }

    protected static void checkCategoriesRanking(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        if (xmcda.categoriesValuesList.size() == 0) {
            errors.addError("No categories values list has been supplied");
        } else if (xmcda.categoriesValuesList.size() > 1) {
            errors.addError("More than one categories values list has been supplied");
        }
        CategoriesValues categoriesValuesList = xmcda.categoriesValuesList.get(0);
        if (!categoriesValuesList.isNumeric()) {
            errors.addError("Each of the categories ranks must be integer");
        }
        Map<String, Integer> categoriesValues = new LinkedHashMap<String, Integer>();
        try {
            CategoriesValues<Integer> categoriesValuesClass = categoriesValuesList.convertTo(Integer.class);
            xmcda.categoriesValuesList.set(0, categoriesValuesClass);

            int min = 100;
            int max = -1;

            for (Map.Entry<Category, LabelledQValues<Integer>> a : categoriesValuesClass.entrySet()) {
                if (a.getValue().get(0).getValue() < min) {
                    min = a.getValue().get(0).getValue();
                }
                if (a.getValue().get(0).getValue() > max) {
                    max = a.getValue().get(0).getValue();
                }
                categoriesValues.put(a.getKey().id(), a.getValue().get(0).getValue());
            }
            if (min != 1) {
                errors.addError("Minimal rank should be equal to 1.");
                return;
            }
            if (max != inputs.categoriesIds.size()) {
                errors.addError("Maximal rank should be equal to number of categories.");
                return;
            }

            for (Map.Entry<String, Integer> categoryA : categoriesValues.entrySet()) {
                for (Map.Entry<String, Integer> categoryB : categoriesValues.entrySet()) {
                    if (categoryA.getValue() == categoryB.getValue() && categoryA.getKey() != categoryB.getKey()) {
                        errors.addError("There can not be two categories with the same rank.");
                        return;
                    }
                }
            }

            inputs.categoriesRanking = categoriesValues;
        } catch (Throwable throwable) {
            errors.addError("An error oceured: " + throwable.getMessage() + ". Remember that each rank has to be integer.");
        }
    }

    protected static void checkAndExtractParameters(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {

        if (xmcda.programParametersList.size() > 1) {
            errors.addError("Only one programParameter is expected");
            return;
        }
        if (xmcda.programParametersList.size() == 0) {
            errors.addError("No programParameter found");
            return;
        }
        if (xmcda.programParametersList.get(0).size() != 2) {
            errors.addError("Parameters' list must contain exactly two elements");
            return;
        }

        checkAndExtractProfilesType(inputs, xmcda, errors);
        checkAndExtractAssignToABetterClass(inputs, xmcda, errors);
    }

    protected static void checkAndExtractProfilesType(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        ComparisonWithProfiles profilesType;

        final ProgramParameter<?> prgParam = xmcda.programParametersList.get(0).get(0);
        if (!"ComparisonWithProfiles".toUpperCase().equals(prgParam.id().toUpperCase())) {
            errors.addError(String.format("Invalid parameter w/ id '%s'", prgParam.id()));
            return;
        }
        if (prgParam.getValues() == null || (prgParam.getValues() != null && prgParam.getValues().size() != 1)) {
            errors.addError("Parameter ComparisonWithProfiles must have a single (label) value only");
            return;
        }
        try {
            final String operatorValue = (String) prgParam.getValues().get(0).getValue();
            profilesType = ComparisonWithProfiles.fromString(operatorValue);
        } catch (Throwable throwable) {
            StringBuffer valid_values = new StringBuffer();
            for (ComparisonWithProfiles op : ComparisonWithProfiles.values()) {
                valid_values.append(op.getLabel()).append(", ");
            }
            String err = "Invalid value for parameter ComparisonWithProfiles, it must be a label, ";
            err += "possible values are: " + valid_values.substring(0, valid_values.length() - 2);
            errors.addError(err);
            profilesType = null;
        }
        inputs.profilesType = profilesType;
    }

    protected static void checkAndExtractAssignToABetterClass(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        Boolean assignToABetterClass;

        final ProgramParameter<?> prgParam2 = xmcda.programParametersList.get(0).get(1);
        if (!"assignToABetterClass".toUpperCase().equals(prgParam2.id().toUpperCase())) {
            errors.addError(String.format("Invalid parameter w/ id '%s'", prgParam2.id()));
            return;
        }
        if (prgParam2.getValues() == null || (prgParam2.getValues() != null && prgParam2.getValues().size() != 1)) {
            errors.addError("Parameter assignToABetterClass must have a single (boolean) value only");
            return;
        }
        try {
            assignToABetterClass = (Boolean) prgParam2.getValues().get(0).getValue();
            if (assignToABetterClass == null) {
                errors.addError("Invalid value for parameter assignToABetterClass, it must be true or false.");
                return;
            }
            inputs.assignToABetterClass = assignToABetterClass;
        } catch (Throwable throwable) {
            String err = "Invalid value for parameter assignToABetterClass, it must be true or false.";
            errors.addError(err);
        }
    }

    //TODO check number of decision makers in each input!
    protected static void checkAndExtractNumberOfDecisionMakers(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        int perfTables = xmcda.performanceTablesList.size();
        int categProfiles = xmcda.categoriesProfilesList.size();
        int flows = xmcda.alternativesValuesList.size() - 1;
        int decisionMakers = 0;

        if (perfTables != categProfiles || perfTables != flows) {
            String err = "Invalid number of files for some of decision makers. Each decision maker need to provide his own categories profiles and performance table list.";
            errors.addError(err);
            return;
        } else {
            decisionMakers = perfTables;
        }

        if (decisionMakers < 2 || decisionMakers > 10) {
            String err = "Invalid number of decision makers. You have to provide files for 2 - 10 decision makers.";
            errors.addError(err);
        }

        inputs.decisionMakers = decisionMakers;
    }

    protected static void checkAndExtractProfilesIds(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        inputs.profilesIds = new ArrayList<>();

        if (xmcda.categoriesProfilesList.size() == 0) {
            errors.addError("No categories profiles list has been supplied");
        }
        if (xmcda.categoriesProfilesList.size() > 10) {
            errors.addError("You can not supply more then 10 categories profiles list");
        }

        inputs.categoryProfiles = new ArrayList<>();
        for (int i = 0; i < inputs.decisionMakers; i++) {
            List<CategoryProfile> categoriesProfilesList = new ArrayList<>();
            CategoriesProfiles categoriesProfiles = xmcda.categoriesProfilesList.get(i);
            if (inputs.categoriesRanking.size() != categoriesProfiles.size()) {
                errors.addError("There is a problem with categories rank list or categories profiles list for decision maker" + (i + 1) + ". Each category has to be added to categories profiles list or each decision maker and to global categories ranks list.");
                return;
            }

            for (Object profile : categoriesProfiles) {
                CategoryProfile tmpProfile = (CategoryProfile) profile;
                if (!tmpProfile.getType().name().equals(inputs.profilesType.toString().toUpperCase())) {
                    errors.addError("There is a problem with categories rank list or categories profiles list for decision maker" + (i + 1) + ". Every decision maker need to provide profiles for categories witch are boundary or central. Profiles type need to be same for all decision makers and equal to setting in program parameters input.");
                    return;
                } else {
                    categoriesProfilesList.add(tmpProfile);
                }
            }

            Collections.sort(categoriesProfilesList, new Comparator<CategoryProfile>() {
                public int compare(CategoryProfile left, CategoryProfile right) {
                    return Integer.compare(inputs.categoriesRanking.get(left.getCategory().id()), inputs.categoriesRanking.get(right.getCategory().id()));
                }
            });

            inputs.categoryProfiles.add(categoriesProfilesList);

            List<String> profilesIds = new ArrayList<>();
            if (inputs.profilesType.toString().toUpperCase().equals("BOUNDING")) {
                checkAndExtractBoundaryProfilesIds(errors, categoriesProfilesList, profilesIds, i);
            } else if (inputs.profilesType.toString().toUpperCase().equals("CENTRAL")) {
                checkAndExtractCentralProfilesIds(errors, categoriesProfilesList, profilesIds, i);
            }
            inputs.profilesIds.add(profilesIds);
        }
        checkForProfilesDuplicates(inputs, errors);
    }

    protected static void checkAndExtractCentralProfilesIds(ProgramExecutionResult errors, List<CategoryProfile> categoriesProfilesList, List<String> profilesIds, int i) {
        for (int j = 0; j < categoriesProfilesList.size(); j++) {
            if (categoriesProfilesList.get(j).getCentralProfile() != null) {
                profilesIds.add(categoriesProfilesList.get(j).getCentralProfile().getAlternative().id());
            } else {
                errors.addError("There is a problem with categories profiles for decision maker" + (i + 1) + ". Every decision maker need to provide profiles for categories witch are boundary or central.");
                break;
            }
        }
    }

    protected static void checkAndExtractBoundaryProfilesIds(ProgramExecutionResult errors, List<CategoryProfile> categoriesProfilesList, List<String> profilesIds, int i) {
        for (int j = 0; j < categoriesProfilesList.size() - 1; j++) {
            if (categoriesProfilesList.get(j).getUpperBound() != null && categoriesProfilesList.get(j + 1).getLowerBound() != null) {
                profilesIds.add(categoriesProfilesList.get(j).getUpperBound().getAlternative().id());
                if (!categoriesProfilesList.get(j).getUpperBound().getAlternative().id().equals(categoriesProfilesList.get(j + 1).getLowerBound().getAlternative().id())) {
                    errors.addError("Each two closest categories have to be separated by same boundary profile.");
                    return;
                }
            } else {
                errors.addError("There is a problem with categories profiles for decision maker" + (i + 1) + ". Every decision maker need to provide profiles for categories witch are boundary or central.");
                return;
            }
        }
    }

    protected static void checkForProfilesDuplicates(Inputs inputs, ProgramExecutionResult errors) {
        HashSet<String> testDuplicates = new HashSet<>();
        for (int i = 0; i < inputs.profilesIds.size(); i++) {
            testDuplicates.addAll(inputs.profilesIds.get(i));
        }
        if (inputs.profilesIds.get(0) != null && testDuplicates.size() != inputs.profilesIds.size() * inputs.profilesIds.get(0).size()) {
            errors.addError("There are some duplicates in decision makers profiles id's.");
        }

    }

    protected static void checkAndExtractCriteria(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        if (xmcda.criteria.getActiveCriteria().size() < 1) {
            errors.addError("You need to provide a not empty criteria list.");
            return;
        }
        inputs.criteriaIds = xmcda.criteria.getActiveCriteria().stream().filter(a -> a.getMarker().equals("criteria")).map(
                Criterion::id).collect(Collectors.toList());
    }

    protected static void checkAndExtractCriteriaPreferencesDirection(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        if (inputs.criteriaIds == null || inputs.criteriaIds.size() < 1) {
            return;
        }

        if (xmcda.criteriaScalesList.size() != 1) {
            errors.addError("You need to provide one not empty criteria scales list.");
            return;
        }

        inputs.criteriaPreferencesDirection = new HashMap<>();

        CriteriaScales criteriaDirection = (CriteriaScales) xmcda.criteriaScalesList.get(0);
        for (Criterion criterion : criteriaDirection.keySet()) {
            @SuppressWarnings("unchecked")
            QuantitativeScale<String> scale = (QuantitativeScale<String>) criteriaDirection.get(criterion).get(0);
            inputs.criteriaPreferencesDirection.put(criterion.id(), scale.getPreferenceDirection().name());
        }
    }

    protected static void checkAndExtractProfilesPerformance(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        if (inputs.profilesIds == null || inputs.profilesIds.size() == 0 || inputs.profilesIds.get(0).size() == 0) {
            return;
        }
        if (xmcda.performanceTablesList.size() < 2 || xmcda.performanceTablesList.size() > 10) {
            errors.addError("You need to provide 2 - 10 profile performances lists.");
            return;
        }

        inputs.profilesPerformance = new ArrayList<>();
        for (int i = 0; i < xmcda.performanceTablesList.size(); i++) {
            @SuppressWarnings("rawtypes")
            PerformanceTable p = xmcda.performanceTablesList.get(i);

            if (p.hasMissingValues()) {
                errors.addError("The performance table has missing values.");
                return;
            }
            if (!p.isNumeric()) {
                errors.addError("The performance table must contain numeric values only");
                return;
            }

            try {
                @SuppressWarnings("unchecked")
                PerformanceTable<Double> perfTable = p.asDouble();
                xmcda.performanceTablesList.set(0, perfTable);
            } catch (Exception e) {
                final String msg = "Error when converting the performance table's value to Double, reason:";
                errors.addError(Utils.getMessage(msg, e));
                return;
            }

            @SuppressWarnings("unchecked")
            PerformanceTable<Double> profilesPerformance = (PerformanceTable<Double>) xmcda.performanceTablesList.get(i);
            Map<String, Map<String, Double>> profilesPerformanceMap = new LinkedHashMap<>();
            for (Alternative alternative : profilesPerformance.getAlternatives()) {
                if (!inputs.profilesIds.get(i).contains(alternative.id())) {
                    continue;
                }
                for (Criterion criterion : profilesPerformance.getCriteria()) {
                    if (!inputs.criteriaIds.contains(criterion.id())) {
                        continue;
                    }

                    Double value = profilesPerformance.getValue(alternative, criterion);
                    profilesPerformanceMap.putIfAbsent(alternative.id(), new LinkedHashMap<>());
                    profilesPerformanceMap.get(alternative.id()).put(criterion.id(), value);
                }
            }
            inputs.profilesPerformance.add(profilesPerformanceMap);
        }

    }

    protected static void checkDominanceCondition(Inputs inputs, ProgramExecutionResult errors) {
        for (int i = 0; i < inputs.profilesIds.size(); i++) {
            for (int j = 0; j < inputs.profilesIds.get(i).size() - 1; j++) {
                for (int criterionIterator = 0; criterionIterator < inputs.criteriaIds.size(); criterionIterator++) {
                    int multiplier = 1;
                    if (inputs.criteriaPreferencesDirection.get(inputs.criteriaIds.get(criterionIterator)).toUpperCase().equals("MIN")) {
                        multiplier = -1;
                    }

                    Double currentPerformance = inputs.profilesPerformance.get(i).get(inputs.profilesIds.get(i).get(j)).get(inputs.criteriaIds.get(criterionIterator));

                    for (int z = 0; z < inputs.profilesIds.size(); z++) {
                        if (z != i) {
                            Double tempPerformance = inputs.profilesPerformance.get(z).get(inputs.profilesIds.get(z).get(j + 1)).get(inputs.criteriaIds.get(criterionIterator));

                            if (currentPerformance * multiplier >= tempPerformance * multiplier ) {
                                errors.addError("Dominance condition is not respected.");
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    protected static void checkAndExtractAlternativesFlows(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        if (xmcda.alternativesValuesList.size() < 3 || xmcda.alternativesValuesList.size() > 11)
        {
            errors.addError("You need to provide 2 - 10 alternatives flows lists.");
        }

        inputs.alternativesFlows = new ArrayList<>();

        for (int i = 0; i < inputs.decisionMakers; i++) {
            AlternativesValues flows = xmcda.alternativesValuesList.get(i+1);
            if (!flows.isNumeric()) {
                errors.addError("Each flow must have numeric type");
            }

            Map<String, Double> tmpFlows = new LinkedHashMap<>();

            try {
                Map<Alternative, LabelledQValues<Double>> flowsMap = flows;
                for (Map.Entry<Alternative, LabelledQValues<Double>> flow : flowsMap.entrySet()) {
                    Double tmpValue = flow.getValue().get(0).convertToDouble().getValue();
                    tmpFlows.put(flow.getKey().id(), tmpValue);
                }
            } catch (Throwable throwable) {
                errors.addError("An error occurred: " + throwable.getMessage() + ". Each flow must have numeric type.");
            }

            for (int j = 0; j < inputs.alternativesIds.size(); j++) {
                boolean found = false;
                for (Object alt : flows.getAlternatives()) {
                    if (((Alternative) alt).id().equals(inputs.alternativesIds.get(j))) {
                        found = true;
                    }
                }
                if (!found) {
                    errors.addError("There are some missing values in alternativesFlows.");
                    return;
                }
            }
            inputs.alternativesFlows.add(tmpFlows);
        }
    }

    protected static void extractFlowsAverage(Inputs inputs) {

        inputs.alternativesFlowsAverage = new LinkedHashMap<>();

        for (int i = 0; i < inputs.alternativesIds.size(); i++) {
            double sum = 0.0;

            for (int j = 0; j < inputs.alternativesFlows.size(); j++) {
                sum += inputs.alternativesFlows.get(j).get(inputs.alternativesIds.get(i));
            }

            sum = sum / inputs.alternativesFlows.size();
            inputs.alternativesFlowsAverage.put(inputs.alternativesIds.get(i), sum);
        }
    }

    protected static void checkAndExtractProfilesFlows(Inputs inputs, XMCDA xmcda, ProgramExecutionResult errors) {
        if (xmcda.alternativesValuesList.size() < 1 || xmcda.alternativesValuesList.size() > 11)
        {
            errors.addError("You need to provide 1 profiles flows lists.");
        }

        AlternativesValues flows = xmcda.alternativesValuesList.get(0);

        if (!flows.isNumeric()) {
            errors.addError("Each flow must have numeric type");
        }

        Map<String, Double> tmpFlows = new LinkedHashMap<>();

        try {
            Map<Alternative, LabelledQValues<Double>> flowsMap = flows;
            for (Map.Entry<Alternative, LabelledQValues<Double>> flow : flowsMap.entrySet()) {
                Double tmpValue = flow.getValue().get(0).convertToDouble().getValue();
                tmpFlows.put(flow.getKey().id(), tmpValue);
            }
        } catch (Throwable throwable) {
            errors.addError("An error occurred: " + throwable.getMessage() + ". Each flow must have numeric type.");
        }

        for (int j = 0; j < inputs.profilesIds.size(); j++) {
            for (int k = 0; k < inputs.profilesIds.get(j).size(); k++) {
                boolean found = false;
                for (Object alt : flows.getAlternatives()) {
                    if (((Alternative) alt).id().equals(inputs.profilesIds.get(j).get(k))) {
                        found = true;
                    }
                }
                if (!found) {
                    errors.addError("There are some missing values in profiles flows.");
                    return;
                }
            }
        }
        inputs.profilesFlows = tmpFlows;
    }
}
