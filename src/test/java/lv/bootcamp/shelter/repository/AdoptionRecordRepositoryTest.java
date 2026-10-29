package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldReturnOnlyMatchingTypeAdoptedSinceDate() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        Animal dog = persistAnimal("Rex", AnimalType.DOG);
        Animal cat = persistAnimal("Mia", AnimalType.CAT);

        AdoptionRecord dogAfter = persistRecord(dog, since.plusMonths(2));
        AdoptionRecord dogAtSince = persistRecord(dog, since);
        persistRecord(dog, since.minusMonths(2));
        persistRecord(cat, since.plusMonths(2));
        entityManager.flush();

        List<AdoptionRecord> recent = adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, since);

        assertThat(recent).extracting(AdoptionRecord::getId)
                .containsExactlyInAnyOrder(dogAfter.getId(), dogAtSince.getId());
    }

    private Animal persistAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        return entityManager.persist(animal);
    }

    private AdoptionRecord persistRecord(Animal animal, LocalDateTime adoptedAt) {
        AdoptionRecord adoption = new AdoptionRecord();
        adoption.setAnimal(animal);
        adoption.setAdopterEmail("anna@example.com");
        adoption.setAdoptedAt(adoptedAt);
        return entityManager.persist(adoption);
    }
}
