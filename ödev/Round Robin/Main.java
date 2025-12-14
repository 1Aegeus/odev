
import java.nio.file.*;
import java.util.*;

public class Main {

    static class surec {
        String surec_adi;
        int gelis_zamani;
        int islem_suresi;   
        int kalan_sure;     
        int bitis_zamani = -1;

        surec(String ad, int gelis, int sure) {
            this.surec_adi = ad;
            this.gelis_zamani = gelis;
            this.islem_suresi = sure;
            this.kalan_sure = sure;
        }
    }

    public static void main(String[] args) throws Exception {

       

        int quantum = 2;
        int baslangic_index = 0;

        
        if (args.length >= 2 && sayi_mi(args[0])) {


            quantum = Integer.parseInt(args[0]);
            baslangic_index = 1;
        }

        if (quantum <= 0) quantum = 1;

        System.out.println("calisma_klasoru: " + System.getProperty("user.dir"));
        System.out.println("quantum: " + quantum);

        if (baslangic_index >= args.length) {



            System.out.println("case dosyasi vermedin.");
            return;
        }

        for (int i = baslangic_index; i < args.length; i++) {



            String case_dosyasi = args[i];

            List<surec> surecler = csv_oku(case_dosyasi);
            String cikti_dosyasi = cikti_adi_uret(case_dosyasi, quantum);

            round_robin_calistir_ve_yaz(surecler, quantum, cikti_dosyasi);

            System.out.println("yazildi: " + Path.of(cikti_dosyasi).toAbsolutePath());
        }

        System.out.println("islem tamamlandi.");
    }

    static boolean sayi_mi(String s) {



        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i == 0 && (c == '-' || c == '+')) continue;
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    static String cikti_adi_uret(String giris_dosyasi, int quantum) {



        String ad = Path.of(giris_dosyasi).getFileName().toString();
        int nokta = ad.lastIndexOf('.');
        if (nokta > 0) ad = ad.substring(0, nokta);
        return "sonuclar/RR_q" + quantum + "_" + ad + ".txt";
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

            liste.add(new surec(ad, gelis, sure));
        }

        
        liste.sort((a, b) -> {



            if (a.gelis_zamani != b.gelis_zamani) return Integer.compare(a.gelis_zamani, b.gelis_zamani);
            return a.surec_adi.compareTo(b.surec_adi);
        });

        return liste;
    }

    static void round_robin_calistir_ve_yaz(List<surec> surecler, int quantum, String cikti_yolu) throws Exception {




        List<Integer> baslangic_zamanlari = new ArrayList<>();
        List<Integer> bitis_zamanlari = new ArrayList<>();
        List<String> calisan_etiketler = new ArrayList<>();

        Queue<surec> hazir_kuyruk = new ArrayDeque<>();

        int zaman = 0;
        int cpu_calisma_suresi = 0;
        int contextswitch_sayisi = 0;
        String onceki_etiket = null;

        int index = 0;
        int n = surecler.size();
        int tamamlanan_sayisi = 0;

        
        if (n > 0 && surecler.get(0).gelis_zamani > 0) {



            zaman_parcasi_ekle(baslangic_zamanlari, bitis_zamanlari, calisan_etiketler, 0, surecler.get(0).gelis_zamani, "BOSTA");
            onceki_etiket = "BOSTA";
            zaman = surecler.get(0).gelis_zamani;
        }

       
        while (index < n && surecler.get(index).gelis_zamani <= zaman) {


            hazir_kuyruk.add(surecler.get(index));
            index++;
        }

        while (tamamlanan_sayisi < n) {


            if (hazir_kuyruk.isEmpty()) {


               
                if (index >= n) break;

                int sonraki_gelis = surecler.get(index).gelis_zamani;

                if (onceki_etiket != null && !onceki_etiket.equals("BOSTA"))
                    contextswitch_sayisi++;

                zaman_parcasi_ekle(baslangic_zamanlari, bitis_zamanlari, calisan_etiketler,
                        zaman, sonraki_gelis, "BOSTA");

                onceki_etiket = "BOSTA";
                zaman = sonraki_gelis;

                while (index < n && surecler.get(index).gelis_zamani <= zaman) {
                    hazir_kuyruk.add(surecler.get(index));
                    index++;
                }
                continue;
            }

            surec calisan = hazir_kuyruk.poll();

            if (onceki_etiket != null && !onceki_etiket.equals(calisan.surec_adi))
                contextswitch_sayisi++;

            int dilim = Math.min(quantum, calisan.kalan_sure);

            zaman_parcasi_ekle(baslangic_zamanlari, bitis_zamanlari, calisan_etiketler,
                    zaman, zaman + dilim, calisan.surec_adi);

            onceki_etiket = calisan.surec_adi;

            zaman += dilim;
            cpu_calisma_suresi += dilim;
            calisan.kalan_sure -= dilim;

            
            while (index < n && surecler.get(index).gelis_zamani <= zaman) {


                hazir_kuyruk.add(surecler.get(index));
                index++;
            }

            if (calisan.kalan_sure > 0) {
                
                hazir_kuyruk.add(calisan);
            } else {
                calisan.bitis_zamani = zaman;
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

        cikti.append("Algoritma: Round Robin\n");
        cikti.append("quantum: ").append(quantum).append("\n\n");

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
