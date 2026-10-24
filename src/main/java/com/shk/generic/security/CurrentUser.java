package com.shk.generic.security;

import java.util.Optional;

/** Who is making the current request, for auditing. Empty for internal or unauthenticated calls. */
public interface CurrentUser {

    Optional<String> username();
}
