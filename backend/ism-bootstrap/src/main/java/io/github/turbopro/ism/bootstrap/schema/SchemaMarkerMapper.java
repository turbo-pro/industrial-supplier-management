package io.github.turbopro.ism.bootstrap.schema;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SchemaMarkerMapper {

    @Insert("""
            INSERT INTO ism_schema_marker (id, marker_code)
            VALUES (#{id}, #{markerCode})
            """)
    int insert(@Param("id") long id, @Param("markerCode") String markerCode);

    @Select("""
            SELECT id, marker_code, created_at
            FROM ism_schema_marker
            WHERE marker_code = #{markerCode}
            """)
    SchemaMarker findByCode(String markerCode);

    @Delete("DELETE FROM ism_schema_marker WHERE marker_code = #{markerCode}")
    int deleteByCode(String markerCode);
}
