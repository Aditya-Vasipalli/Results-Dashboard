package edu.vit.results.repo;

import edu.vit.results.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {}
