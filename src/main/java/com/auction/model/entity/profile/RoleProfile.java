package com.auction.model.entity.profile;

import com.auction.model.enums.Role;
import java.io.Serializable;

public interface RoleProfile extends Serializable {
    Role getRole();
}
