
import java.nio.file.*;
import java.util.*;

public class Main {

    static class surec {


        String surec_adi;
        int gelis_zamani;
        int islem_suresi;
        int kalan_sure;
        int oncelik;          
        int bitis_zamani = -1;
        boolean tamamlandi = false;

        surec(String ad, int gelis, int sure, int oncelik) {
            this.surec_adi = ad;
            this.gelis_zamani = gelis;
            this.islem_suresi = sure;
            this.kalan_sure = sure;
            this.oncelik = oncelik;
        }
    }

    public static void main(String[] args) throws Exception {


        


        System.out.println("calisma_klasoru: " + System.getProperty("user.dir"));

        for (int i = 0; i < args.length; i++) {


            String case_dosyasi = args[i];

            List<surec> surecler = csv_oku(case_dosyasi);
            String cikti_dosyasi = cikti_adi_uret(case_dosyasi);

            priority_preemptive_calistir_ve_yaz(surecler, cikti_dosyasi);

            System.out.println("yazildi: " + Path.of(cikti_dosyasi).toAbsolutePath());
        }

        System.out.println("islem tamamlandi.");
    }

    static String cikti_adi_uret(String giris_dosyasi) {


        String ad = Path.of(giris_dosyasi).getFileName().toString();
        int nokta = ad.lastIndexOf('.');
        if (nokta > 0) ad = ad.substring(0, nokta);
        return "sonuclar/PRIORITY_preemptive_" + ad + ".txt";
    }

    
    static int oncelik_cevir(String deger) {


        if (deger == null) return 2;
        String s = deger.trim().toLowerCase();

        if (s.equals("high")) return 1;
        if (s.equals("normal") || s.equals("medium")) return 2;
        if (s.equals("low")) return 3;

        boolean sayi = true;
        for (int i = 0; i < s.length(); i++) {


            char c = s.charAt(i);
            if (i == 0 && (c == '-' || c == '+')) continue;
            if (c < '0' || c > '9') { sayi = false; break; }
        }
        if (sayi) {
            try { return Integer.parseInt(s); } catch (Exception e) { return 2; }
        }

        return 2;
    }

    
    static List<surec> csv_oku(String dosya_yolu) throws Exception {



        List<String> satirlar = Files.readAllLines(Path.of(dosya_yolu));
        List<surec> liste = new ArrayList<>();

        for (int i = 0; i < satirlar.size(); i++) {
            String satir = satirlar.get(i).trim();
            if (satir.isEmpty()) continue;

            if (i == 0 && satir.toLowerCase().contains("arrival")) continue;

            String[] parcalar = satir.split(",");
            String ad = parcalar[0].trim();
            int gelis = Integer.parseInt(parcalar[1].trim());
            int sure  = Integer.parseInt(parcalar[2].trim());

            int oncelik = 2;
            if (parcalar.length >= 4) oncelik = oncelik_cevir(parcalar[3]);

            liste.add(new surec(ad, gelis, sure, oncelik));
        }

        
        liste.sort((a, b) -> {


            if (a.gelis_zamani != b.gelis_zamani) return Integer.compare(a.gelis_zamani, b.gelis_zamani);
            return a.surec_adi.compareTo(b.surec_adi);
        });

        return liste;
    }

    static void priority_preemptive_calistir_ve_yaz(List<surec> surecler, String cikti_yolu) throws Exception {



        List<Integer> baslangic_zamanlari = new ArrayList<>();
        List<Integer> bitis_zamanlari = new ArrayList<>();
        List<String> calisan_etiketler = new ArrayList<>();

        int zaman = 0;
        int cpu_calisma_suresi = 0;

        int contextswitch_sayisi = 0;
        String onceki_etiket = null;

        int tamamlanan_sayisi = 0;
        int n = surecler.size();

        if (n > 0 && surecler.get(0).gelis_zamani > 0) {


            zaman_parcasi_ekle(baslangic_zamanlari, bitis_zamanlari, calisan_etiketler,
                    0, surecler.get(0).gelis_zamani, "BOSTA");
            onceki_etiket = "BOSTA";
            zaman = surecler.get(0).gelis_zamani;
        }

        while (tamamlanan_sayisi < n) {


            surec secilen = null;

            for (surec s : surecler) {


                if (!s.tamamlandi && s.gelis_zamani <= zaman) {


                    if (secilen == null) {

                        secilen = s;
                    } else {
                        if (s.oncelik < secilen.oncelik) {

                            secilen = s;
                        } else if (s.oncelik == secilen.oncelik) {

                            if (s.kalan_sure < secilen.kalan_sure) secilen = s;
                            else if (s.kalan_sure == secilen.kalan_sure) {
                                if (s.gelis_zamani < secilen.gelis_zamani) secilen = s;
                                else if (s.gelis_zamani == secilen.gelis_zamani &&
                                         s.surec_adi.compareTo(secilen.surec_adi) < 0) secilen = s;
                            }
                        }
                    }
                }
            }

            if (secilen == null) {


                int sonraki_gelis = Integer.MAX_VALUE;
                for (surec s : surecler) {

                    if (!s.tamamlandi) {

                        sonraki_gelis = Math.min(sonraki_gelis, s.gelis_zamani);
                    }
                }
                if (sonraki_gelis == Integer.MAX_VALUE) break;

                if (onceki_etiket != null && !onceki_etiket.equals("BOSTA"))
                    contextswitch_sayisi++;

                zaman_parcasi_ekle(baslangic_zamanlari, bitis_zamanlari, calisan_etiketler,
                        zaman, sonraki_gelis, "BOSTA");

                onceki_etiket = "BOSTA";
                zaman = sonraki_gelis;
                continue;
            }

            if (onceki_etiket != null && !onceki_etiket.equals(secilen.surec_adi))
                contextswitch_sayisi++;

            zaman_parcasi_ekle(baslangic_zamanlari, bitis_zamanlari, calisan_etiketler,
                    zaman, zaman + 1, secilen.surec_adi);

            onceki_etiket = secilen.surec_adi;

            zaman += 1;
            cpu_calisma_suresi += 1;
            secilen.kalan_sure -= 1;

            if (secilen.kalan_sure == 0) {


                secilen.tamamlandi = true;
                secilen.bitis_zamani = zaman;
                tamamlanan_sayisi++;
            }
        }

        Map<String, Integer> bekleme_sureleri = new LinkedHashMap<>();
        Map<String, Integer> tamamlanma_sureleri = new LinkedHashMap<>();

        for (surec s : surecler) {
            int tamamlanma = s.bitis_zamani - s.gelis_zamani;
            int bekleme = tamamlanma - s.islem_suresi;
            bekleme_sureleri.put(s.surec_adi, bekleme);
            tamamlanma_sureleri.put(s.surec_adi, tamamlanma);
        }

        int[] zaman_araliklari = {50, 100, 150, 200};
        Map<Integer, Integer> throughput = new LinkedHashMap<>();

        for (int t : zaman_araliklari) {

            
            int sayac = 0;
            for (surec s : surecler)
                if (s.bitis_zamani <= t) sayac++;
            throughput.put(t, sayac);
        }

        double contextswitch_maliyeti = contextswitch_sayisi * 0.001;
        double toplam_zaman = zaman + contextswitch_maliyeti;
        double cpu_verimliligi = (toplam_zaman == 0) ? 0 : (cpu_calisma_suresi / toplam_zaman);

        StringBuilder cikti = new StringBuilder();

        cikti.append("Algoritma: Priority Preemptive\n\n");
        cikti.append("Not: kucuk oncelik sayisi = daha yuksek oncelik (high=1, normal=2, low=3)\n\n");

        cikti.append("Zaman Cizelgesi:\n");
        for (int i = 0; i < calisan_etiketler.size(); i++) {
            cikti.append(String.format("[ %3d ] -- %s -- [ %3d ]\n",
                    baslangic_zamanlari.get(i),
                    calisan_etiketler.get(i),
                    bitis_zamanlari.get(i)));
        }

        cikti.append("\nBekleme Sureleri:\n");
        bekleme_sureleri.forEach((k,v) -> cikti.append(k).append(": ").append(v).append("\n"));

        cikti.append("\nTamamlanma Sureleri:\n");
        tamamlanma_sureleri.forEach((k,v) -> cikti.append(k).append(": ").append(v).append("\n"));

        cikti.append("\nThroughput:\n");
        throughput.forEach((k,v) -> cikti.append("T=").append(k).append(": ").append(v).append("\n"));

        cikti.append("\nContextSwitch Sayisi: ").append(contextswitch_sayisi).append("\n");
        cikti.append(String.format("CPU Verimliligi: %.5f\n", cpu_verimliligi));

        try {
            Path yol = Path.of(cikti_yolu);
            Files.createDirectories(yol.getParent());
            Files.writeString(yol, cikti.toString());
        } catch (Exception e) {
            System.out.println("dosya_yazma_hatasi!");
            e.printStackTrace();
        }
    }

    static void zaman_parcasi_ekle(List<Integer> bas, List<Integer> bit, List<String> etiket,
                                   int b, int s, String e) {

        if (b == s) return;

        if (!etiket.isEmpty()) {
            int son = etiket.size() - 1;
            if (etiket.get(son).equals(e) && bit.get(son) == b) {
                bit.set(son, s);
                return;
            }
        }

        bas.add(b);
        bit.add(s);
        etiket.add(e);
    }
}
