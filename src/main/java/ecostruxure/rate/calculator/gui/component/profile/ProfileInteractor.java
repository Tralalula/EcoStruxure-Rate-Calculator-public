package ecostruxure.rate.calculator.gui.component.profile;

import ecostruxure.rate.calculator.be.*;
import ecostruxure.rate.calculator.be.enums.ResourceType;
import ecostruxure.rate.calculator.bll.service.GeographyService;
import ecostruxure.rate.calculator.bll.service.HistoryService;
import ecostruxure.rate.calculator.bll.service.ProfileService;
import ecostruxure.rate.calculator.bll.utils.RateUtils;
import ecostruxure.rate.calculator.gui.component.modals.addprofile.AddProfileGeographyItemModel;
import ecostruxure.rate.calculator.gui.component.profile.ProfileModel.ProfileTableType;
import ecostruxure.rate.calculator.gui.system.currency.CurrencyManager;
import ecostruxure.rate.calculator.gui.system.currency.CurrencyManager.CurrencyType;
import javafx.beans.binding.Bindings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProfileInteractor {
    private static final BigDecimal INITIAL_VALUE = new BigDecimal("0.00");

    private final ProfileModel model;


    private ProfileService profileService;
    private HistoryService historyService;
    private GeographyService geographyService;
    private Profile profile;
    private List<AddProfileGeographyItemModel> addProfileGeographyItemModels;
    private List<ProfileTeamItemModel> teamItemModels;
    private List<ProfileHistoryItemModel> historyItemModels;
    private BigDecimal contributedHours = INITIAL_VALUE;
    private BigDecimal totalHourlyRate = INITIAL_VALUE;
    private BigDecimal totalDayRate = INITIAL_VALUE;
    private BigDecimal totalAnnualCost = INITIAL_VALUE;
    private ProfileSaveModel originalSaveModel;
    private LocalDateTime currentDateTime = LocalDateTime.now();

    public ProfileInteractor(ProfileModel model, Runnable onFetchError) {
        this.model = model;

        try {
            profileService = new ProfileService();
            geographyService = new GeographyService();
            historyService = new HistoryService();
        } catch (Exception e) {
            onFetchError.run();
        }

        setupBindings();
    }

    public boolean fetchProfile(int id) {
        try {
            currentDateTime = LocalDateTime.now();
            contributedHours = INITIAL_VALUE;
            totalHourlyRate = INITIAL_VALUE;
            totalDayRate = INITIAL_VALUE;
            totalAnnualCost = INITIAL_VALUE;
            profile = profileService.get(id);
            historyItemModels = convertToHistoryModels(historyService.getProfileHistory(id));
            historyItemModels.addFirst(convertProfileToHistory(profile));
            addProfileGeographyItemModels = convertToGeographyModels(geographyService.all());
            teamItemModels = convertToTeamModels(profileService.getTeams(profile));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void updateModelPostFetchProfile() {
        model.idProperty().set(profile.id());
        model.profileName().set(profile.profileData().name());
        model.saveModel().nameProperty().set(profile.profileData().name());
        model.saveModel().selectedResourceTypeProperty().set(profile.profileData().overhead() ? ResourceType.OVERHEAD : ResourceType.PRODUCTION);
        model.archivedProperty().set(profile.profileData().archived());

        model.saveModel().annualSalaryProperty().set(String.valueOf(profile.annualSalary()));
        model.saveModel().annualFixedAmountProperty().set(String.valueOf(profile.fixedAnnualAmount()));
        model.saveModel().overheadMultiplierProperty().set(String.valueOf(profile.overheadMultiplier()));
        model.saveModel().annualEffectiveWorkingHoursProperty().set(String.valueOf(profile.effectiveWorkHours()));
        model.saveModel().hoursPerDayProperty().set(String.valueOf(profile.hoursPerDay()));

        model.saveModel().locations().setAll(addProfileGeographyItemModels);
        model.teams().setAll(teamItemModels);
        model.history().setAll(historyItemModels);

        if (contributedHours == null) contributedHours = INITIAL_VALUE;
        if (totalHourlyRate == null) totalHourlyRate = INITIAL_VALUE;
        if (totalDayRate == null) totalDayRate = INITIAL_VALUE;
        if (totalAnnualCost == null) totalAnnualCost = INITIAL_VALUE;

        model.contributedHours().set(contributedHours.toString());
        model.setTotalHourlyRate(totalHourlyRate);
        model.setTotalDayRate(totalDayRate);
        model.setTotalAnnualCost(totalAnnualCost);
        model.currentDateProperty().set(currentDateTime);
        updateSelectedGeography();
        originalSaveModel = cloneProfileToSaveModel(model.saveModel());
        setupBindings();
    }

    public boolean saveProfile() {
        try {
            boolean saved = profileService.update(profile, createProfileFromModel());
            if (saved) fetchProfile(model.idProperty().get());
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public void changeHistoryModel(ProfileHistoryItemModel historyModel) {
        model.historySelectedProperty().set(!historyModel.updatedAtProperty().get().equals(currentDateTime));

        model.saveModel().selectedResourceTypeProperty().set(historyModel.resourceTypeProperty().get());
        model.saveModel().annualSalaryProperty().set(historyModel.annualSalaryProperty().get().toString());
        model.saveModel().annualFixedAmountProperty().set(historyModel.fixedAnnualAmountProperty().get().toString());
        model.saveModel().overheadMultiplierProperty().set(historyModel.overheadMultiplierProperty().get().toString());
        model.saveModel().annualEffectiveWorkingHoursProperty().set(historyModel.effectiveWorkHoursProperty().get().toString());
        model.saveModel().hoursPerDayProperty().set(historyModel.hoursPerDayProperty().get().toString());
    }

    public void updateModelPostSave() {
        updateModelPostFetchProfile();
        var firstHistory = model.history().getFirst();
        model.historySelectedProperty().set(false);
        model.saveModel().selectedResourceTypeProperty().set(firstHistory.resourceTypeProperty().get());

        CurrencyType currentCurrency = CurrencyManager.currencyTypeProperty().get();
        BigDecimal annualSalaryInEUR;
        BigDecimal fixedAnnualAmountInEUR;

        if (currentCurrency == CurrencyType.USD) {
            BigDecimal conversionRateToEUR = CurrencyManager.conversionRateProperty().get().stripTrailingZeros();

            annualSalaryInEUR = firstHistory.annualSalaryProperty().get().divide(conversionRateToEUR, 4, RoundingMode.HALF_UP);
            fixedAnnualAmountInEUR = firstHistory.fixedAnnualAmountProperty().get().divide(conversionRateToEUR, 4, RoundingMode.HALF_UP);
        } else {
            annualSalaryInEUR = firstHistory.annualSalaryProperty().get();
            fixedAnnualAmountInEUR = firstHistory.fixedAnnualAmountProperty().get();
        }

        model.saveModel().annualSalaryProperty().set(annualSalaryInEUR.toString());
        model.saveModel().annualFixedAmountProperty().set(fixedAnnualAmountInEUR.toString());
        model.saveModel().overheadMultiplierProperty().set(firstHistory.overheadMultiplierProperty().get().toString());
        model.saveModel().annualEffectiveWorkingHoursProperty().set(firstHistory.effectiveWorkHoursProperty().get().toString());
        model.saveModel().hoursPerDayProperty().set(firstHistory.hoursPerDayProperty().get().toString());
    }

    public void clearModel() {
        model.profileName().set("");
        model.location().set("");
        model.saveModel().selectedGeographyProperty().set(null);
        model.teams().clear();
        model.contributedHours().set("0.00");
        model.setTotalHourlyRate(INITIAL_VALUE);
        model.setTotalDayRate(INITIAL_VALUE);
        model.setTotalAnnualCost(INITIAL_VALUE);
        model.selectedTableTypeProperty().set(ProfileTableType.TEAM);
        model.selectedHistoryItemProperty().set(null);
        model.historySelectedProperty().set(false);
    }

    public void undoChanges() {
        if (originalSaveModel == null) return;

        ProfileSaveModel current = model.saveModel();
        current.nameProperty().set(originalSaveModel.nameProperty().get());
        current.selectedGeographyProperty().set(originalSaveModel.selectedGeographyProperty().get());
        current.selectedResourceTypeProperty().set(originalSaveModel.selectedResourceTypeProperty().get());
        current.annualSalaryProperty().set(originalSaveModel.annualSalaryProperty().get());
        current.annualFixedAmountProperty().set(originalSaveModel.annualFixedAmountProperty().get());
        current.annualEffectiveWorkingHoursProperty().set(originalSaveModel.annualEffectiveWorkingHoursProperty().get());
        current.overheadMultiplierProperty().set(originalSaveModel.overheadMultiplierProperty().get());
        current.hoursPerDayProperty().set(originalSaveModel.hoursPerDayProperty().get());
    }

    private void updateSelectedGeography() {
        var geographyId = profile.profileData().geography();
        var geographyItem = model.saveModel().locations()
                                             .stream()
                                             .filter(geography -> geography.idProperty().get() == geographyId)
                                             .findFirst()
                                             .orElse(null);

        model.saveModel().selectedGeographyProperty().set(geographyItem);
        if (geographyItem != null) model.location().set(geographyItem.nameProperty().get());
    }


    // Model conversions

    private Profile createProfileFromModel() {
        ProfileSaveModel saveModel = model.saveModel();

        var profileData = new ProfileData();
        profileData.id(model.idProperty().get());
        profileData.name((saveModel.nameProperty().get()));
        profileData.currency(saveModel.currencyProperty().get().name());
        profileData.geography(saveModel.selectedGeographyProperty().get().idProperty().get());
        profileData.overhead(saveModel.selectedResourceTypeProperty().get() == ResourceType.OVERHEAD);
        profileData.archived(false);

        var profile = new Profile();
        profile.id(model.idProperty().get());

        profile.annualSalary(new BigDecimal(saveModel.annualSalaryProperty().get()));
        profile.fixedAnnualAmount(new BigDecimal(saveModel.annualFixedAmountProperty().get()));
        profile.overheadMultiplier(new BigDecimal(saveModel.overheadMultiplierProperty().get()));
        profile.effectiveWorkHours(new BigDecimal(saveModel.annualEffectiveWorkingHoursProperty().get()));
        profile.hoursPerDay(new BigDecimal(saveModel.hoursPerDayProperty().get()));
        profile.profileData(profileData);

        return profile;
    }

    private ProfileSaveModel cloneProfileToSaveModel(ProfileSaveModel original) {
        ProfileSaveModel clone = new ProfileSaveModel();

        clone.nameProperty().set(original.nameProperty().get());
        clone.selectedGeographyProperty().set(original.selectedGeographyProperty().get());
        clone.selectedResourceTypeProperty().set(original.selectedResourceTypeProperty().get());
        clone.annualSalaryProperty().set(original.annualSalaryProperty().get());
        clone.annualFixedAmountProperty().set(original.annualFixedAmountProperty().get());
        clone.annualEffectiveWorkingHoursProperty().set(original.annualEffectiveWorkingHoursProperty().get());
        clone.overheadMultiplierProperty().set(original.overheadMultiplierProperty().get());
        clone.hoursPerDayProperty().set(original.hoursPerDayProperty().get());

        return clone;
    }

    private ProfileHistoryItemModel convertProfileToHistory(Profile profile) {
        ProfileHistoryItemModel historyModel = new ProfileHistoryItemModel();

        historyModel.idProperty().set(profile.id());

        if (profile.profileData().overhead()) historyModel.resourceTypeProperty().set(ResourceType.OVERHEAD);
        else historyModel.resourceTypeProperty().set(ResourceType.PRODUCTION);

        historyModel.setAnnualSalary(profile.annualSalary());
        historyModel.setFixedAnnualAmount(profile.fixedAnnualAmount());
        historyModel.overheadMultiplierProperty().set(profile.overheadMultiplier());
        historyModel.effectiveWorkHoursProperty().set(profile.effectiveWorkHours());
        historyModel.hoursPerDayProperty().set(profile.hoursPerDay());
        historyModel.updatedAtProperty().set(currentDateTime);
        historyModel.setHourlyRate(profileService.hourlyRate(profile));
        historyModel.setDayRate(profileService.dayRate(profile));
        historyModel.setAnnualCost(profileService.annualCost(profile));

        return historyModel;
    }

    private List<ProfileHistoryItemModel> convertToHistoryModels(List<ProfileHistory> history) {
        List<ProfileHistoryItemModel> historyModels = new ArrayList<>();

        for (ProfileHistory profile : history) {
            ProfileHistoryItemModel historyModel = new ProfileHistoryItemModel();

            historyModel.idProperty().set(profile.profileId());

            if (profile.overhead()) historyModel.resourceTypeProperty().set(ResourceType.OVERHEAD);
            else historyModel.resourceTypeProperty().set(ResourceType.PRODUCTION);

            historyModel.setAnnualSalary(profile.annualSalary());
            historyModel.setFixedAnnualAmount(profile.fixedAnnualAmount());
            historyModel.overheadMultiplierProperty().set(profile.overheadMultiplier());
            historyModel.effectiveWorkHoursProperty().set(profile.effectiveWorkHours());
            historyModel.hoursPerDayProperty().set(profile.hoursPerDay());
            historyModel.updatedAtProperty().set(profile.updatedAt());
            historyModel.setHourlyRate(profileService.hourlyRate(profile));
            historyModel.setDayRate(profileService.dayRate(profile));
            historyModel.setAnnualCost(profileService.annualCost(profile));
            historyModels.add(historyModel);
        }

        return historyModels;
    }

    private List<AddProfileGeographyItemModel> convertToGeographyModels(List<Geography> geographies) {
        List<AddProfileGeographyItemModel> geographyModels = new ArrayList<>();

        for (Geography geography : geographies) {
            AddProfileGeographyItemModel geographyModel = new AddProfileGeographyItemModel();
            geographyModel.idProperty().set(geography.id());
            geographyModel.nameProperty().set(geography.name());
            geographyModels.add(geographyModel);
        }

        return geographyModels;
    }

    private List<ProfileTeamItemModel> convertToTeamModels(List<Team> teams) throws Exception {
        List<ProfileTeamItemModel> teamModels = new ArrayList<>();

        for (Team team : teams) {
            if (team.archived()) continue;
            ProfileTeamItemModel teamModel = new ProfileTeamItemModel();
            teamModel.idProperty().set(team.id());
            teamModel.nameProperty().set(team.name());
            BigDecimal utilizationRate = profileService.getProfileRateUtilizationForTeam(profile.id(), team.id());
            BigDecimal utilizationHours = profileService.getProfileHourUtilizationForTeam(profile.id(), team.id());

            teamModel.utilizationCostProperty().set(utilizationRate);
            teamModel.utilizationHoursProperty().set(utilizationHours);

            BigDecimal hourlyRate = RateUtils.hourlyRate(profile, utilizationRate);
            BigDecimal dayRate = RateUtils.dayRate(profile, utilizationRate);
            BigDecimal annualCost = RateUtils.annualCost(profile, utilizationRate);

            BigDecimal annualEffectiveWorkingHours = RateUtils.utilizedHours(profile, utilizationHours);

            teamModel.setHourlyRate(hourlyRate);
            teamModel.setDayRate(dayRate);
            teamModel.setAnnualCost(annualCost);
            teamModel.annualEffectiveWorkingHoursProperty().set(annualEffectiveWorkingHours);

            contributedHours = contributedHours.add(annualEffectiveWorkingHours);
            totalHourlyRate = totalHourlyRate.add(hourlyRate);
            totalDayRate = totalDayRate.add(dayRate);
            totalAnnualCost = totalAnnualCost.add(annualCost);
            teamModels.add(teamModel);
        }

        return teamModels;
    }

    // Data validation & bindings
    private void setupBindings() {
        configureValidationBindings();
        configureSaveBindings();
        configureUndoBindings();
        configureCheckoutBindings();
    }


    private void configureValidationBindings() {
        var saveModel = model.saveModel();
        saveModel.nameIsValidProperty().bind(model.saveModel().nameProperty().isNotEmpty());
        saveModel.selectedGeographyIsValidProperty().bind(model.saveModel().selectedGeographyProperty().isNotNull());
        saveModel.annualSalaryIsValidProperty().bind(model.saveModel().annualSalaryProperty().isNotEmpty());
        saveModel.annualFixedAmountIsValidProperty().bind(model.saveModel().annualFixedAmountProperty().isNotEmpty());
        saveModel.annualEffectiveWorkingHoursIsValidProperty().bind(model.saveModel().annualEffectiveWorkingHoursProperty().isNotEmpty());
        saveModel.overheadMultiplierIsValidProperty().bind(model.saveModel().overheadMultiplierProperty().isNotEmpty());

        model.saveModel().disableFieldsProperty().bind(model.historySelectedProperty());
    }

    private void configureSaveBindings() {
        model.okToSaveProperty().unbind();
        model.okToSaveProperty().bind(Bindings.createBooleanBinding(
                () -> isDataValid() && hasDataChanged(),
                model.saveModel().nameProperty(),
                model.saveModel().selectedGeographyProperty(),
                model.saveModel().selectedResourceTypeProperty(),
                model.saveModel().annualSalaryProperty(),
                model.saveModel().annualFixedAmountProperty(),
                model.saveModel().annualEffectiveWorkingHoursProperty(),
                model.saveModel().overheadMultiplierProperty(),
                model.saveModel().hoursPerDayProperty()
        ));
    }

    private void configureUndoBindings() {
        model.okToUndoProperty().unbind();
        model.okToUndoProperty().bind(Bindings.createBooleanBinding(
                this::hasDataChanged,
                model.saveModel().nameProperty(),
                model.saveModel().selectedGeographyProperty(),
                model.saveModel().selectedResourceTypeProperty(),
                model.saveModel().annualSalaryProperty(),
                model.saveModel().annualFixedAmountProperty(),
                model.saveModel().annualEffectiveWorkingHoursProperty(),
                model.saveModel().overheadMultiplierProperty(),
                model.saveModel().hoursPerDayProperty())
        );
    }

    private void configureCheckoutBindings() {
        var historyModel = model.selectedHistoryItemProperty();

        model.okToCheckoutProperty().unbind();
        model.okToCheckoutProperty().bind(Bindings.createBooleanBinding(
                () -> doesHistoryDifferFromOriginal(historyModel.get()),
                model.historySelectedProperty(),
                model.saveModel().selectedResourceTypeProperty(),
                model.saveModel().annualSalaryProperty(),
                model.saveModel().annualFixedAmountProperty(),
                model.saveModel().overheadMultiplierProperty(),
                model.saveModel().annualEffectiveWorkingHoursProperty(),
                model.saveModel().hoursPerDayProperty())
        );
    }

    public boolean archiveProfile(boolean shouldArchive) {
        try {
            Profile profile = profileService.get(model.idProperty().get());
            return profileService.archive(profile, shouldArchive);
        } catch (Exception e) {
            return false;
        }
    }

    public void updateArchivedProfile(boolean archived) {
        model.archivedProperty().set(archived);
    }

    private boolean doesHistoryDifferFromOriginal(ProfileHistoryItemModel historyModel) {
        if (historyModel == null || originalSaveModel == null) return false;

        if (!model.historySelectedProperty().get()) return false;

        return !historyModel.resourceTypeProperty().get().equals(originalSaveModel.selectedResourceTypeProperty().get()) ||
                !historyModel.annualSalaryProperty().get().equals(new BigDecimal(originalSaveModel.annualSalaryProperty().get())) ||
                !historyModel.fixedAnnualAmountProperty().get().equals(new BigDecimal(originalSaveModel.annualFixedAmountProperty().get())) ||
                !historyModel.overheadMultiplierProperty().get().equals(new BigDecimal(originalSaveModel.overheadMultiplierProperty().get())) ||
                !historyModel.effectiveWorkHoursProperty().get().equals(new BigDecimal(originalSaveModel.annualEffectiveWorkingHoursProperty().get())) ||
                !historyModel.hoursPerDayProperty().get().equals(new BigDecimal(originalSaveModel.hoursPerDayProperty().get()));
    }

    private boolean isDataValid() {
        return !model.saveModel().nameProperty().get().isEmpty() &&
                model.saveModel().selectedGeographyProperty().get() != null &&
                !model.saveModel().annualSalaryProperty().get().isEmpty() &&
                !model.saveModel().annualFixedAmountProperty().get().isEmpty() &&
                !model.saveModel().annualEffectiveWorkingHoursProperty().get().isEmpty() &&
                !model.saveModel().overheadMultiplierProperty().get().isEmpty() &&
                !model.saveModel().hoursPerDayProperty().get().isEmpty();
    }

    private boolean hasDataChanged() {
        if (originalSaveModel == null) return false;
        return !model.saveModel().nameProperty().get().equals(originalSaveModel.nameProperty().get()) ||
                model.saveModel().selectedGeographyProperty().get() != originalSaveModel.selectedGeographyProperty().get() ||
                model.saveModel().selectedResourceTypeProperty().get() != originalSaveModel.selectedResourceTypeProperty().get() ||
                !model.saveModel().annualSalaryProperty().get().equals(originalSaveModel.annualSalaryProperty().get()) ||
                !model.saveModel().annualFixedAmountProperty().get().equals(originalSaveModel.annualFixedAmountProperty().get()) ||
                !model.saveModel().annualEffectiveWorkingHoursProperty().get().equals(originalSaveModel.annualEffectiveWorkingHoursProperty().get()) ||
                !model.saveModel().overheadMultiplierProperty().get().equals(originalSaveModel.overheadMultiplierProperty().get()) ||
                !model.saveModel().hoursPerDayProperty().get().equals(originalSaveModel.hoursPerDayProperty().get());
    }
}