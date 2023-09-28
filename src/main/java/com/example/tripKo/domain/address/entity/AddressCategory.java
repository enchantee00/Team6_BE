package com.example.tripKo.domain.address.entity;

import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@DynamicUpdate
@DynamicInsert
@Entity
@NoArgsConstructor(access= AccessLevel.PROTECTED)
public class AddressCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private long id;

    @Column(nullable = false)
    private String emdName;

    @Column(nullable = false)
    private String siggName;

    @Column(nullable = false)
    private String sidoName;


    @Builder
    public AddressCategory(String emdName, String siggName, String sidoName) {
        this.emdName = emdName;
        this.siggName = siggName;
        this.sidoName = sidoName;
    }
}
