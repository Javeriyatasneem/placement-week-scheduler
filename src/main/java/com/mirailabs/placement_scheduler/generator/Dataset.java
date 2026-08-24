package com.mirailabs.placement_scheduler.generator;

import com.mirailabs.placement_scheduler.model.Company;
import com.mirailabs.placement_scheduler.model.Room;
import com.mirailabs.placement_scheduler.model.Student;

import java.util.List;

/**
 * Simple bundle of everything the generator produces. Passed into the
 * scheduler as its input.
 */
public class Dataset {
    private final List<Company> companies;
    private final List<Student> students;
    private final List<Room> rooms;

    public Dataset(List<Company> companies, List<Student> students, List<Room> rooms) {
        this.companies = companies;
        this.students = students;
        this.rooms = rooms;
    }

    public List<Company> getCompanies() { return companies; }
    public List<Student> getStudents() { return students; }
    public List<Room> getRooms() { return rooms; }
}
