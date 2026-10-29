package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    @Test
    void evaluateAdoption_shouldRejectWhenAdopterNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ADOPTER_NOT_FOUND);
    }

    @Test
    void evaluateAdoption_shouldRejectWhenAnimalNotFound() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(eligibleAdopter()));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ANIMAL_NOT_FOUND);
    }

    @Test
    void evaluateAdoption_shouldRejectWhenAnimalNotAvailable() {
        Animal animal = availableDog();
        animal.setStatus(AnimalStatus.ADOPTED);
        givenAdopterAndAnimal(eligibleAdopter(), animal);

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ANIMAL_NOT_AVAILABLE);
    }

    @Test
    void evaluateAdoption_shouldRejectUnderageAdopter() {
        Adopter adopter = eligibleAdopter();
        adopter.setAge(17);
        givenAdopterAndAnimal(adopter, availableDog());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
    }

    @ParameterizedTest
    @CsvSource({"false, 3", "true, 5"})
    void evaluateAdoption_shouldRejectWhenPetLimitReached(boolean largeProperty, int petCount) {
        Adopter adopter = eligibleAdopter();
        adopter.setLargeProperty(largeProperty);
        adopter.setCurrentPetCount(petCount);
        givenAdopterAndAnimal(adopter, availableDog());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void evaluateAdoption_shouldRejectExoticAnimalWithoutPermit(AnimalType exoticType) {
        Animal animal = availableDog();
        animal.setType(exoticType);
        givenAdopterAndAnimal(eligibleAdopter(), animal);

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.NO_EXOTIC_PERMIT);
    }

    @ParameterizedTest
    @CsvSource({
            // previousAdoptions, largeProperty, animalAge, petCount, expectedScore
            "0, false,  , 0,  0",
            "1, false,  , 0, 10",
            "3, false,  , 0, 10",
            "4, false,  , 0, 15",
            "0, true,   , 0, 15",
            "0, false, 7, 0,  0",
            "0, false, 8, 0, 20",
            "1, false,  , 2,  6",
            "0, false,  , 3,  0"
    })
    void calculatePriorityScore_shouldApplyBonusesAndPetPenalty(
            int previousAdoptions, boolean largeProperty, Integer animalAge, int petCount, int expectedScore) {
        Adopter adopter = eligibleAdopter();
        adopter.setPreviousAdoptions(previousAdoptions);
        adopter.setLargeProperty(largeProperty);
        adopter.setCurrentPetCount(petCount);
        Animal animal = availableDog();
        animal.setAge(animalAge);

        assertThat(service.calculatePriorityScore(adopter, animal)).isEqualTo(expectedScore);
    }

    @Test
    void evaluateAdoption_shouldApproveAndNotifyEligibleAdopter() {
        Adopter adopter = eligibleAdopter();
        adopter.setPreviousAdoptions(1);
        givenAdopterAndAnimal(adopter, availableDog());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result).isEqualTo(AdoptionResult.approved(10));
        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, 10);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
    }

    private void givenAdopterAndAnimal(Adopter adopter, Animal animal) {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    private void assertRejected(AdoptionResult result, String reason) {
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(reason);
    }

    private Adopter eligibleAdopter() {
        Adopter adopter = new Adopter();
        adopter.setName("Anna");
        adopter.setEmail("anna@example.com");
        adopter.setAge(30);
        return adopter;
    }

    private Animal availableDog() {
        Animal animal = new Animal();
        animal.setName("Rex");
        animal.setType(AnimalType.DOG);
        animal.setStatus(AnimalStatus.AVAILABLE);
        return animal;
    }
}
