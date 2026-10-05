package com.zerotap.service;

import com.zerotap.dto.UserDto;
import com.zerotap.model.TrustedContact;
import com.zerotap.model.User;
import com.zerotap.repository.TrustedContactRepository;
import com.zerotap.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TrustedContactRepository contactRepository;

    public UserService(UserRepository userRepository, TrustedContactRepository contactRepository) {
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
    }

    public UserDto getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }

    public List<TrustedContact> getTrustedContacts(String userId) {
        return contactRepository.findByUserIdOrderByIsPrimaryDesc(userId);
    }

    public TrustedContact addTrustedContact(String userId, TrustedContact contact) {
        contact.setId(UUID.randomUUID().toString());
        contact.setUserId(userId);
        return contactRepository.save(contact);
    }
}
