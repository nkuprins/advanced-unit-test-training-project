package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ticket: ANIMAL-3 (see README).
 * findByStatus_shouldReturnOnlyMatchingAnimals is already implemented —
 * mirror this pattern (and AdopterRepositoryTest's constraint test) to add
 * the second method yourself. You'll also need to create
 * AdoptionRecordRepositoryTest from scratch (see README).
 */
@DataJpaTest
class AnimalRepositoryTest {

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByStatus_shouldReturnOnlyMatchingAnimals() {
        entityManager.persist(newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE));
        entityManager.persist(newAnimal("Mia", AnimalType.CAT, AnimalStatus.ADOPTED));
        entityManager.flush();

        var available = animalRepository.findByStatus(AnimalStatus.AVAILABLE);

        assertThat(available).hasSize(1);
        assertThat(available.getFirst().getName()).isEqualTo("Rex");
    }

    @Test
    void save_shouldFailWhenMicrochipIdIsDuplicated() {
        entityManager.persistAndFlush(newAnimal("Rex", AnimalType.DOG, AnimalStatus.AVAILABLE, "CHIP-123"));

        Animal duplicate = newAnimal("Max", AnimalType.DOG, AnimalStatus.AVAILABLE, "CHIP-123");

        // Hibernate docs: "The IDENTITY generator must execute the insert right after calling persist()"
        // Otherwise, I would have used flush here as well.
        assertThatThrownBy(() -> animalRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Animal newAnimal(String name, AnimalType type, AnimalStatus status) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(status);
        return animal;
    }

    private Animal newAnimal(String name, AnimalType type, AnimalStatus status, String microchipId) {
        Animal animal = newAnimal(name, type, status);
        animal.setMicrochipId(microchipId);
        return animal;
    }
}
