package app.mylauncher.gtv;

/**
 * The cities you can choose from: the capital of every governorate in Egypt and every region in Saudi Arabia, plus
 * the other big cities. Coordinates are rounded to two decimals (about a kilometre), enough for weather and, later,
 * prayer times. To add a city, add one line to the right list.
 */
final class Cities {
    private Cities() {}

    static final class City {
        final String country;
        final String en;
        final String ar;
        final double lat;
        final double lon;

        City(String country, String en, String ar, double lat, double lon) {
            this.country = country;
            this.en = en;
            this.ar = ar;
            this.lat = lat;
            this.lon = lon;
        }

        /** What is saved to remember the choice, for example "EG|Cairo". */
        String key() {
            return country + "|" + en;
        }

        String name(boolean arabic) {
            return arabic ? ar : en;
        }
    }

    static final City[] EGYPT = {
        new City("EG", "Cairo", "القاهرة", 30.04, 31.24),
        new City("EG", "Giza", "الجيزة", 30.01, 31.21),
        new City("EG", "6th of October", "السادس من أكتوبر", 29.93, 30.92),
        new City("EG", "New Cairo", "القاهرة الجديدة", 30.03, 31.47),
        new City("EG", "Alexandria", "الإسكندرية", 31.20, 29.92),
        new City("EG", "Port Said", "بورسعيد", 31.27, 32.30),
        new City("EG", "Suez", "السويس", 29.97, 32.55),
        new City("EG", "Ismailia", "الإسماعيلية", 30.60, 32.27),
        new City("EG", "Damietta", "دمياط", 31.42, 31.81),
        new City("EG", "Mansoura", "المنصورة", 31.04, 31.38),
        new City("EG", "Zagazig", "الزقازيق", 30.59, 31.50),
        new City("EG", "10th of Ramadan", "العاشر من رمضان", 30.29, 31.75),
        new City("EG", "Banha", "بنها", 30.47, 31.18),
        new City("EG", "Tanta", "طنطا", 30.79, 31.00),
        new City("EG", "Kafr El Sheikh", "كفر الشيخ", 31.11, 30.94),
        new City("EG", "Shibin El Kom", "شبين الكوم", 30.56, 31.01),
        new City("EG", "Damanhur", "دمنهور", 31.04, 30.47),
        new City("EG", "Fayoum", "الفيوم", 29.31, 30.84),
        new City("EG", "Beni Suef", "بني سويف", 29.07, 31.10),
        new City("EG", "Minya", "المنيا", 28.11, 30.75),
        new City("EG", "Asyut", "أسيوط", 27.18, 31.18),
        new City("EG", "Sohag", "سوهاج", 26.56, 31.70),
        new City("EG", "Qena", "قنا", 26.16, 32.72),
        new City("EG", "Luxor", "الأقصر", 25.70, 32.64),
        new City("EG", "Aswan", "أسوان", 24.09, 32.90),
        new City("EG", "Hurghada", "الغردقة", 27.26, 33.81),
        new City("EG", "Marsa Alam", "مرسى علم", 25.07, 34.89),
        new City("EG", "Marsa Matruh", "مرسى مطروح", 31.35, 27.24),
        new City("EG", "Siwa", "سيوة", 29.20, 25.52),
        new City("EG", "Kharga", "الخارجة", 25.45, 30.55),
        new City("EG", "Arish", "العريش", 31.13, 33.80),
        new City("EG", "Sharm El Sheikh", "شرم الشيخ", 27.91, 34.33),
        new City("EG", "Dahab", "دهب", 28.50, 34.51),
        new City("EG", "El Tor", "الطور", 28.24, 33.62),
    };

    static final City[] SAUDI = {
        new City("SA", "Riyadh", "الرياض", 24.71, 46.68),
        new City("SA", "Jeddah", "جدة", 21.54, 39.17),
        new City("SA", "Makkah", "مكة المكرمة", 21.39, 39.86),
        new City("SA", "Madinah", "المدينة المنورة", 24.47, 39.61),
        new City("SA", "Dammam", "الدمام", 26.43, 50.10),
        new City("SA", "Khobar", "الخبر", 26.28, 50.21),
        new City("SA", "Dhahran", "الظهران", 26.29, 50.11),
        new City("SA", "Jubail", "الجبيل", 27.01, 49.66),
        new City("SA", "Qatif", "القطيف", 26.56, 50.01),
        new City("SA", "Ras Tanura", "رأس تنورة", 26.64, 50.16),
        new City("SA", "Hofuf", "الهفوف", 25.38, 49.59),
        new City("SA", "Hafar Al-Batin", "حفر الباطن", 28.43, 45.97),
        new City("SA", "Taif", "الطائف", 21.27, 40.42),
        new City("SA", "Al Lith", "الليث", 20.15, 40.27),
        new City("SA", "Al Qunfudhah", "القنفذة", 19.13, 41.08),
        new City("SA", "Rabigh", "رابغ", 22.80, 39.03),
        new City("SA", "Yanbu", "ينبع", 24.09, 38.06),
        new City("SA", "Al Ula", "العلا", 26.61, 37.92),
        new City("SA", "Tabuk", "تبوك", 28.38, 36.57),
        new City("SA", "Duba", "ضباء", 27.35, 35.69),
        new City("SA", "Buraidah", "بريدة", 26.33, 43.97),
        new City("SA", "Unaizah", "عنيزة", 26.08, 43.99),
        new City("SA", "Hail", "حائل", 27.52, 41.70),
        new City("SA", "Al Majmaah", "المجمعة", 25.90, 45.35),
        new City("SA", "Dawadmi", "الدوادمي", 24.51, 44.39),
        new City("SA", "Afif", "عفيف", 23.91, 42.92),
        new City("SA", "Al Kharj", "الخرج", 24.15, 47.31),
        new City("SA", "Wadi ad-Dawasir", "وادي الدواسر", 20.49, 44.79),
        new City("SA", "Abha", "أبها", 18.22, 42.51),
        new City("SA", "Khamis Mushait", "خميس مشيط", 18.31, 42.73),
        new City("SA", "Bisha", "بيشة", 19.99, 42.60),
        new City("SA", "Najran", "نجران", 17.49, 44.13),
        new City("SA", "Sharurah", "شرورة", 17.47, 47.11),
        new City("SA", "Jazan", "جازان", 16.89, 42.55),
        new City("SA", "Al Baha", "الباحة", 20.01, 41.47),
        new City("SA", "Sakaka", "سكاكا", 29.97, 40.21),
        new City("SA", "Al Qurayyat", "القريات", 31.33, 37.34),
        new City("SA", "Arar", "عرعر", 30.98, 41.02),
        new City("SA", "Rafha", "رفحاء", 29.62, 43.49),
    };

    /** The saved choice back to its city, or null if nothing was chosen (or the list changed). */
    static City find(String key) {
        if (key == null) return null;
        for (City c : EGYPT) if (c.key().equals(key)) return c;
        for (City c : SAUDI) if (c.key().equals(key)) return c;
        return null;
    }
}
