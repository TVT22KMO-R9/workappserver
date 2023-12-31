package com.backend.server.reportedhours;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.backend.server.companies.Company;
import com.backend.server.reportedhours.DTO.WorkDayResponseDTO;
import com.backend.server.security.SecurityService;
import com.backend.server.users.User;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.backend.server.utility.HolidayChecker;

@Service
@RequiredArgsConstructor
public class WorkDayService {
    private final WorkDayRepository workDayRepository;
    private final SecurityService securityService;
    private final HolidayChecker holidayChecker;

    

    public WorkDay saveWorkDay(WorkDay workDay) {
        return workDayRepository.save(workDay);
    }

    public WorkDay getWorkDayById(Long id) {
        return workDayRepository.findById(id).orElse(null);
    }

    public String deleteWorkDay(Long id) {
        workDayRepository.deleteById(id);
        return "WorkDay removed !! " + id;
    }

    public List<WorkDay> getWorkDaysById(Long id) {
        return workDayRepository.findAllByUserId(id);
    }

    public List<WorkDay> getCompanyWorkDays(Long companyID) {
        return workDayRepository.findAllByUserIn(companyID);
    }

    public List<WorkDay> getCompanyWorkDaysExcludingUser(User user) {
        return workDayRepository.findAllByUserExcludingUser(user.getId(), user.getCompany().getId());
    }

    @Transactional
    public WorkDay addShift(String token, LocalDate date, LocalTime startTime, 
                            LocalTime endTime, Integer breaksTotal, String description) {
        // käyttäjä tokenista
        User user = securityService.getUserFromToken(token);

        // Tarkista onko päivälle jo olemassa entry
        Optional<WorkDay> existingWorkDay = workDayRepository.findByUserAndDate(user.getId(), date);

        WorkDay workDay;
        if(existingWorkDay.isPresent()) {
            // Jos olemassa, päivitä olemassa oleva entry
            workDay = existingWorkDay.get();
            // Ehkä tarkistus jos päivitetään vuoroa joka on aloitettu mutta ei ole lopetettu ? 
            
        } else {
            // Else tee uusi työpäiväentry
            workDay = new WorkDay();
            workDay.setUser(user);
            workDay.setDate(date);
        }

        // Loput tiedot
        workDay.setStartTime(startTime);
        workDay.setEndTime(endTime);
        workDay.setBreaksTotal(breaksTotal);
        workDay.setDescription(description);

        // Tallennus
        return workDayRepository.save(workDay);
    }

    @Transactional
    public WorkDay punchIn(User user, LocalTime startTime){
        WorkDay workDay = new WorkDay();
        workDay.setUser(user);
        workDay.setDate(LocalDate.now());
        workDay.setStartTime(startTime);
        workDay.setIsHoliday(holidayChecker.isHoliday(LocalDate.now()));
        workDay.setBreaksTotal(null);
        workDay.setDescription(null);
        return workDayRepository.save(workDay);
        
    }

    @Transactional
    public WorkDay punchOut(User user, LocalTime endTime){
        WorkDay workDay = workDayRepository.findByUserAndDate(user.getId(), LocalDate.now()).orElse(null);
        if (workDay == null) {
            throw new IllegalArgumentException("No started workday");
        }
        workDay.setEndTime(endTime);
        return workDayRepository.save(workDay);
    }

    @Transactional
    public WorkDayResponseDTO updateShift(User user, LocalDate date, LocalTime startTime, 
                               LocalTime endTime, Integer breaksTotal, String description) {
                                
        // tarkista onko päivä tulevaisuudessa
        if(date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Can't fill future dates");
        } 

        // Tarkista onko päivälle jo olemassa entry
        Optional<WorkDay> existingWorkDay = workDayRepository.findByUserAndDate(user.getId(), date);
     
        WorkDay workDay;
        if(existingWorkDay.isPresent()) {
            // Jos olemassa, päivitä olemassa oleva entry
            workDay = existingWorkDay.get();
            System.out.println("workday: " + workDay);
            // Ehkä tarkistus jos päivitetään vuoroa joka on aloitettu mutta ei ole lopetettu
            
        } else {
            // Else tee uusi työpäiväentry
            workDay = new WorkDay();
            workDay.setUser(user);
            workDay.setDate(date);
        }
      

        // pyhäpäivä tarkistus
        Boolean isHoliday = holidayChecker.isHoliday(date);
        //Boolean isHoliday = false;

        // conversio date -> localdate
        

        // Loput tiedot
        workDay.setStartTime(startTime);
        workDay.setEndTime(endTime);
        workDay.setBreaksTotal(breaksTotal);
        workDay.setDescription(description);
        workDay.setIsHoliday(isHoliday);



        // Tallennus
         WorkDay savedWorkDay = workDayRepository.save(workDay);

        // Palautus
        WorkDayResponseDTO workDayResponseDTO = new WorkDayResponseDTO();
        workDayResponseDTO.setDate(savedWorkDay.getDate());
        workDayResponseDTO.setStartTime(savedWorkDay.getStartTime());
        workDayResponseDTO.setEndTime(savedWorkDay.getEndTime());

        return workDayResponseDTO;
    }

    public List<WorkDay> getUserShifts(User user) {  // OVERLOAD, Hae default määrä 31
        return getUserShifts(user, 31);
    }

    public List<WorkDay> getUserShifts(User user, Integer limit) {  // HAE RAJALLINEN MÄÄRÄ VUOROJA
        return workDayRepository.findLastShiftsForUser(user.getId(), limit);
    }

    public void deleteShiftByID(Long id) {
        workDayRepository.deleteById(id);
    }

    public WorkDay findByID (Long id) {
        return workDayRepository.findById(id).orElse(null);
    }

    @Transactional
    public void deleteOldWorkDays(int days, Company company){
        LocalDate cutOff = LocalDate.now().minusDays(days);
        workDayRepository.deleteOldReports(cutOff, company.getId());
    }

    
    
}
