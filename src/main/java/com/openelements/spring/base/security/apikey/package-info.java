/**
 * Servlet filter that implements the {@code X-API-Key} authentication mechanism described in the
 * {@linkplain com.openelements.spring.base.security parent package documentation}.
 *
 * <p>The data and persistence side of API keys (entities, DTOs, repositories, key generation and
 * hashing) lives in {@link com.openelements.spring.base.services.apikey} so that the filter can
 * remain a thin authentication adapter.
 */
package com.openelements.spring.base.security.apikey;
