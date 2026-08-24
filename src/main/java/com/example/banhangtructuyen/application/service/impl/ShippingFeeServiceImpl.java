package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.shipping.ShippingFeeResult;
import com.example.banhangtructuyen.application.dto.shipping.ValidatedShippingSelection;
import com.example.banhangtructuyen.application.service.ShippingFeeService;
import com.example.banhangtructuyen.config.ShippingFeeProperties;
import com.example.banhangtructuyen.domain.model.ShippingMethod;
import com.example.banhangtructuyen.domain.model.ShippingRegion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ShippingFeeServiceImpl implements ShippingFeeService {

    private static final Set<String> LOCAL_PROVINCES = Set.of("ha noi", "hanoi");
    private static final Set<String> NEARBY_PROVINCES = Set.of("bac ninh", "hung yen", "hai duong");
    private static final Set<String> SUPPORTED_PROVINCES = Set.of(
            "an giang",
            "ba ria vung tau",
            "bac giang",
            "bac kan",
            "bac lieu",
            "bac ninh",
            "ben tre",
            "binh dinh",
            "binh duong",
            "binh phuoc",
            "binh thuan",
            "ca mau",
            "can tho",
            "cao bang",
            "da nang",
            "dak lak",
            "dak nong",
            "dien bien",
            "dong nai",
            "dong thap",
            "gia lai",
            "ha giang",
            "ha nam",
            "ha noi",
            "ha tinh",
            "hai duong",
            "hai phong",
            "hanoi",
            "hau giang",
            "ho chi minh",
            "ho chi minh city",
            "hoa binh",
            "hue",
            "hung yen",
            "khanh hoa",
            "kien giang",
            "kon tum",
            "lai chau",
            "lam dong",
            "lang son",
            "lao cai",
            "long an",
            "nam dinh",
            "nghe an",
            "ninh binh",
            "ninh thuan",
            "phu tho",
            "phu yen",
            "quang binh",
            "quang nam",
            "quang ngai",
            "quang ninh",
            "quang tri",
            "soc trang",
            "son la",
            "tay ninh",
            "thai binh",
            "thai nguyen",
            "thanh hoa",
            "thua thien hue",
            "tien giang",
            "tra vinh",
            "tuyen quang",
            "vinh long",
            "vinh phuc",
            "yen bai"
    );
    private static final Set<String> ADMINISTRATIVE_PREFIXES = Set.of("tinh ", "thanh pho ", "tp ");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern MULTIPLE_WHITESPACE = Pattern.compile("\\s+");

    private final ShippingFeeProperties shippingFeeProperties;

    @Override
    public ShippingFeeResult calculate(final ValidatedShippingSelection selection) {
        validateSelection(selection);

        final ShippingRegion region = resolveRegion(selection.province());
        final ShippingMethod shippingMethod = selection.shippingMethod();
        if (region == ShippingRegion.OTHER && shippingMethod == ShippingMethod.EXPRESS) {
            throw new IllegalArgumentException("EXPRESS shipping is not supported for region OTHER");
        }

        final BigDecimal shippingFee = shippingFeeProperties.requireConfiguredFee(region, shippingMethod);
        return new ShippingFeeResult(region, shippingMethod, shippingFee);
    }

    private static void validateSelection(final ValidatedShippingSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Validated shipping selection is required");
        }
        if (selection.province() == null || selection.province().isBlank()) {
            throw new IllegalArgumentException("Province is required");
        }
        if (selection.shippingMethod() == null) {
            throw new IllegalArgumentException("Shipping method is required");
        }
    }

    private static ShippingRegion resolveRegion(final String province) {
        final String normalizedProvince = normalizeProvince(province);
        if (!SUPPORTED_PROVINCES.contains(normalizedProvince)) {
            throw new IllegalArgumentException("Province is not supported: " + province);
        }
        if (LOCAL_PROVINCES.contains(normalizedProvince)) {
            return ShippingRegion.LOCAL;
        }
        if (NEARBY_PROVINCES.contains(normalizedProvince)) {
            return ShippingRegion.NEARBY;
        }
        return ShippingRegion.OTHER;
    }

    private static String normalizeProvince(final String province) {
        String normalized = Normalizer.normalize(province, Normalizer.Form.NFD)
                .replace('Đ', 'D')
                .replace('đ', 'd');
        normalized = DIACRITICS.matcher(normalized).replaceAll("")
                .toLowerCase(Locale.ROOT);
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ");
        normalized = MULTIPLE_WHITESPACE.matcher(normalized).replaceAll(" ").trim();

        for (String prefix : ADMINISTRATIVE_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length()).trim();
            }
        }
        return normalized;
    }
}
